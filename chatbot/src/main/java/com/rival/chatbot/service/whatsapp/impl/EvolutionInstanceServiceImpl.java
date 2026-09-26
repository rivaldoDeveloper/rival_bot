package com.rival.chatbot.service.whatsapp.impl;

import com.rival.chatbot.domain.whatsapp.WhatsAppAccountEntity;
import com.rival.chatbot.repository.whatsapp.WhatsAppAccountRepository;
import com.rival.chatbot.service.whatsapp.EvolutionInstanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class EvolutionInstanceServiceImpl implements EvolutionInstanceService {

    private static final Logger log = LoggerFactory.getLogger(EvolutionInstanceServiceImpl.class);

    @Value("${evolution.api.url:http://localhost:8081}")
    private String evolutionApiUrl;

    @Value("${evolution.api.global-apikey:sua_chave_global_aqui}")
    private String globalApiKey;

    private final WhatsAppAccountRepository repository;
    private final RestTemplate restTemplate;

    public EvolutionInstanceServiceImpl(WhatsAppAccountRepository repository) {
        this.repository = repository;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public String createInstanceAndGetQR(UUID tenantId) {
        log.info("1. Iniciando conexão (Modo QR) para tenant: {}", tenantId);
        HttpHeaders headers = buildHeaders();

        WhatsAppAccountEntity account = repository.findAll().stream()
                .filter(acc -> tenantId.equals(acc.getTenantId()))
                .findFirst()
                .orElse(new WhatsAppAccountEntity());

        account.setTenantId(tenantId);

        if (account.getInstanceName() != null) {
            try {
                log.info("Limpando instância antiga para garantir o Modo QR: {}", account.getInstanceName());
                restTemplate.exchange(
                        evolutionApiUrl + "/instance/delete/" + account.getInstanceName(),
                        HttpMethod.DELETE,
                        new HttpEntity<>(headers),
                        String.class
                );
            } catch (Exception ignored) {}
        }

        String newInstanceName = "tenant_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        account.setInstanceName(newInstanceName);
        account.setEvolutionApiKey(globalApiKey);
        repository.save(account);

        log.info("2. Criando NOVA instância ({}) no Modo QR Code...", newInstanceName);

        try {
            Map<String, Object> createBody = new HashMap<>();
            createBody.put("instanceName", newInstanceName);
            createBody.put("token", newInstanceName);
            createBody.put("qrcode", true);
            createBody.put("integration", "WHATSAPP-BAILEYS");

            restTemplate.exchange(
                    evolutionApiUrl + "/instance/create",
                    HttpMethod.POST,
                    new HttpEntity<>(createBody, headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            // ARQUITETURA RESILIENTE: Auto-Ativa o Webhook imediatamente após recriar a instância!
            try { activateBot(tenantId); } catch (Exception e) { log.warn("Aviso na auto-ativação do webhook: {}", e.getMessage()); }

        } catch (Exception e) {
            log.warn("Aviso ao criar instância QR: {}", e.getMessage());
        }

        String base64Qr = null;
        log.info("3. Solicitando imagem do QR Code à API...");
        for (int i = 1; i <= 15; i++) {
            try {
                Thread.sleep(3000);
                ResponseEntity<Map<String, Object>> connectResponse = restTemplate.exchange(
                        evolutionApiUrl + "/instance/connect/" + newInstanceName,
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        new ParameterizedTypeReference<Map<String, Object>>() {}
                );
                base64Qr = extrairQrCode(connectResponse.getBody());
                if (base64Qr != null && !base64Qr.isBlank()) {
                    break;
                }
            } catch (Exception ignored) {}
        }

        if (base64Qr != null && !base64Qr.isBlank()) {
            log.info("4. QR Code gerado com sucesso! Aguardando leitura segura.");
            return base64Qr;
        }
        throw new IllegalStateException("A Evolution API não conseguiu gerar a imagem do QR Code a tempo. Tente novamente.");
    }

    @Override
    public String getFreshQR(UUID tenantId) {
        WhatsAppAccountEntity account = repository.findAll().stream()
                .filter(acc -> tenantId.equals(acc.getTenantId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Nenhuma instância encontrada."));

        if (account.getInstanceName() == null) {
            throw new IllegalArgumentException("Nenhuma instância encontrada.");
        }

        ResponseEntity<Map<String, Object>> connectResponse = restTemplate.exchange(
                evolutionApiUrl + "/instance/connect/" + account.getInstanceName(),
                HttpMethod.GET,
                new HttpEntity<>(buildHeaders()),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        String base64Qr = extrairQrCode(connectResponse.getBody());
        if (base64Qr != null && !base64Qr.isBlank()) {
            return base64Qr;
        }
        throw new IllegalStateException("O WhatsApp já está conectado ou a imagem ainda está a ser gerada.");
    }

    @Override
    public void activateBot(UUID tenantId) {
        WhatsAppAccountEntity account = repository.findAll().stream()
                .filter(acc -> tenantId.equals(acc.getTenantId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Instância não encontrada. Conecte o WhatsApp primeiro."));

        if (account.getInstanceName() == null) {
            throw new IllegalArgumentException("Instância não encontrada. Conecte o WhatsApp primeiro.");
        }

        Map<String, Object> webhookConfig = new HashMap<>();
        webhookConfig.put("enabled", true);
        webhookConfig.put("url", "http://host.docker.internal:8080/api/v1/whatsapp/webhook/" + account.getInstanceName());
        webhookConfig.put("byEvents", false);
        webhookConfig.put("base64", false);
        webhookConfig.put("events", List.of("MESSAGES_UPSERT"));

        Map<String, Object> webhookBody = new HashMap<>();
        webhookBody.put("webhook", webhookConfig);

        restTemplate.exchange(
                evolutionApiUrl + "/webhook/set/" + account.getInstanceName(),
                HttpMethod.POST,
                new HttpEntity<>(webhookBody, buildHeaders()),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        log.info(">> Webhook ativado com segurança para a instância {}!", account.getInstanceName());
    }

    @Override
    @Transactional
    public void logoutInstance(UUID tenantId) {
        log.info("Limpando instância antiga para o tenant: {}", tenantId);
        WhatsAppAccountEntity account = repository.findAll().stream()
                .filter(acc -> tenantId.equals(acc.getTenantId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Nenhuma instância ativa encontrada."));

        if (account.getInstanceName() != null) {
            try {
                restTemplate.exchange(
                        evolutionApiUrl + "/instance/delete/" + account.getInstanceName(),
                        HttpMethod.DELETE,
                        new HttpEntity<>(buildHeaders()),
                        String.class
                );
            } catch (Exception e) {
                log.warn("Instância não estava ativa na Evolution API, prosseguindo com a limpeza local.");
            }
            repository.delete(account);
        }
    }

    @Override
    public String getPairingCode(UUID tenantId, String phoneNumber) {
        log.info("Iniciando requisição de Pairing Code para o tenant: {}, número: {}", tenantId, phoneNumber);
        String cleanNumber = phoneNumber.replaceAll("[^0-9]", "");

        WhatsAppAccountEntity account = repository.findAll().stream()
                .filter(acc -> tenantId.equals(acc.getTenantId()))
                .findFirst()
                .orElse(new WhatsAppAccountEntity());

        account.setTenantId(tenantId);

        if (account.getInstanceName() != null) {
            try {
                log.info("Limpando instância antiga bloqueada: {}", account.getInstanceName());
                restTemplate.exchange(
                        evolutionApiUrl + "/instance/delete/" + account.getInstanceName(),
                        HttpMethod.DELETE,
                        new HttpEntity<>(buildHeaders()),
                        String.class
                );
            } catch (Exception ignored) {
                log.info("A instância antiga já não estava ativa no Docker.");
            }
        }

        String newInstanceName = "tenant_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        account.setInstanceName(newInstanceName);
        account.setEvolutionApiKey(globalApiKey);
        repository.save(account);

        log.info("1. Criando NOVA instância ({}) já no Modo Numérico...", newInstanceName);

        try {
            Map<String, Object> createBody = new HashMap<>();
            createBody.put("instanceName", newInstanceName);
            createBody.put("token", newInstanceName);
            createBody.put("qrcode", false);
            createBody.put("number", cleanNumber);
            createBody.put("integration", "WHATSAPP-BAILEYS");

            restTemplate.exchange(
                    evolutionApiUrl + "/instance/create",
                    HttpMethod.POST,
                    new HttpEntity<>(createBody, buildHeaders()),
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            // ARQUITETURA RESILIENTE: Auto-Ativa o Webhook imediatamente após recriar a instância!
            try { activateBot(tenantId); } catch (Exception e) { log.warn("Aviso na auto-ativação do webhook: {}", e.getMessage()); }

            Thread.sleep(3000);
        } catch (Exception e) {
            log.error("Erro ao criar nova instância: ", e);
            throw new IllegalStateException("Falha ao criar o motor do WhatsApp. Verifique se o Docker está ligado.");
        }

        log.info("2. Solicitando o código de emparelhamento final à API...");

        String url = evolutionApiUrl + "/instance/connect/" + newInstanceName + "?number=" + cleanNumber;
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("number", cleanNumber);

        for (int i = 1; i <= 10; i++) {
            try {
                ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        new HttpEntity<>(requestBody, buildHeaders()),
                        new ParameterizedTypeReference<Map<String, Object>>() {}
                );

                Map<String, Object> responseBody = response.getBody();

                if (responseBody != null) {
                    if (responseBody.containsKey("pairingCode") && responseBody.get("pairingCode") != null) {
                        String code = String.valueOf(responseBody.get("pairingCode"));
                        if (!code.isBlank() && !code.equals("null")) {
                            log.info("✅ Pairing code gerado com sucesso: {}", code);
                            return code;
                        }
                    }
                    if (responseBody.containsKey("code") && responseBody.get("code") != null) {
                        String code = String.valueOf(responseBody.get("code"));
                        if (!code.isBlank() && !code.equals("null") && code.length() <= 12 && !code.contains("@")) {
                            log.info("✅ Pairing code gerado com sucesso (chave 'code'): {}", code);
                            return code;
                        }
                    }
                }

                log.info("Tentativa {}/10: Motor Baileys ainda a inicializar. Aguardando 2 segundos...", i);
                Thread.sleep(2000);
            } catch (Exception e) {
                log.warn("Tentativa {}/10 falhou: {}", i, e.getMessage());
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            }
        }

        throw new IllegalStateException("A Meta (WhatsApp) bloqueou a geração do código para este número temporariamente.");
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", globalApiKey);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private String extrairQrCode(Map<String, Object> body) {
        if (body == null) return null;

        if (body.containsKey("qrcode")) {
            Object qrObj = body.get("qrcode");
            if (qrObj instanceof Map<?, ?> qrMap) {
                if (qrMap.containsKey("base64")) return (String) qrMap.get("base64");
            } else if (qrObj instanceof String qrString) {
                return qrString;
            }
        }

        if (body.containsKey("base64")) return (String) body.get("base64");

        if (body.containsKey("data")) {
            Object dataObj = body.get("data");
            if (dataObj instanceof Map<?, ?> dataMap) {
                return extrairQrCode((Map<String, Object>) dataMap);
            }
        }

        if (body.containsKey("instance")) {
            Object instObj = body.get("instance");
            if (instObj instanceof Map<?, ?> instMap) {
                return extrairQrCode((Map<String, Object>) instMap);
            }
        }

        return null;
    }
}