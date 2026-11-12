package com.rival.chatbot.service.whatsapp.impl;

import com.rival.chatbot.domain.whatsapp.WhatsAppAccountEntity;
import com.rival.chatbot.repository.whatsapp.WhatsAppAccountRepository;
import com.rival.chatbot.service.whatsapp.EvolutionInstanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
@SuppressWarnings({"rawtypes", "unchecked"})
public class EvolutionInstanceServiceImpl implements EvolutionInstanceService {

    private static final Logger log = LoggerFactory.getLogger(EvolutionInstanceServiceImpl.class);

    @Value("${evolution.api.url}")
    private String evolutionApiUrl;

    @Value("${evolution.api.global-apikey}")
    private String globalApiKey;

    @Value("${evolution.webhook.base-url}")
    private String webhookBaseUrl;

    private final WhatsAppAccountRepository repository;
    private final RestTemplate restTemplate;

    private final Map instanceCacheByTenant = new ConcurrentHashMap();

    public EvolutionInstanceServiceImpl(WhatsAppAccountRepository repository) {
        this.repository = repository;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(8000);
        this.restTemplate = new RestTemplate(factory);
    }

    private WhatsAppAccountEntity findAccountByTenant(UUID tenantId) {
        if (tenantId == null) return null;

        Object cachedName = instanceCacheByTenant.get(tenantId);
        if (cachedName instanceof String) {
            WhatsAppAccountEntity byName = (WhatsAppAccountEntity) repository.findByInstanceName((String) cachedName).orElse(null);
            if (byName != null) {
                return byName;
            }
            instanceCacheByTenant.remove(tenantId);
        }

        List all = repository.findAll();
        for (Object obj : all) {
            WhatsAppAccountEntity acc = (WhatsAppAccountEntity) obj;
            if (tenantId.equals(acc.getTenantId())) {
                if (acc.getInstanceName() != null) {
                    instanceCacheByTenant.put(tenantId, acc.getInstanceName());
                }
                return acc;
            }
        }
        return null;
    }

    /**
     * Envia LOGOUT imediato para desvincular o aparelho no celular e agenda o DELETE
     * para 3.5 segundos depois em background (nao trava a tela e garante que o celular desconecte!).
     */
    private void disconnectInPhoneAndDeleteAsync(String instanceName) {
        if (instanceName == null || instanceName.isBlank()) return;
        HttpHeaders headers = buildHeaders();

        try {
            log.info("Enviando comando de LOGOUT para desvincular o celular na instancia: {}", instanceName);
            restTemplate.exchange(
                    evolutionApiUrl + "/instance/logout/" + instanceName,
                    HttpMethod.DELETE,
                    new HttpEntity(headers),
                    String.class
            );
        } catch (Exception e) {
            log.debug("Aviso no logout da instancia {}: {}", instanceName, e.getMessage());
        }

        // Deleta a instancia da Evolution API apos 3.5 segundos em background
        // (tempo necessario para o Baileys avisar os servidores do WhatsApp para tirar da lista do celular)
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(3500);
                restTemplate.exchange(
                        evolutionApiUrl + "/instance/delete/" + instanceName,
                        HttpMethod.DELETE,
                        new HttpEntity(headers),
                        String.class
                );
                log.info("Instancia {} removida da Evolution API apos desvinculacao do celular.", instanceName);
            } catch (Exception ignored) {}
        });
    }

    private void deleteSyncInEvolution(String instanceName) {
        if (instanceName == null || instanceName.isBlank()) return;
        HttpHeaders headers = buildHeaders();
        try {
            restTemplate.exchange(
                    evolutionApiUrl + "/instance/logout/" + instanceName,
                    HttpMethod.DELETE,
                    new HttpEntity(headers),
                    String.class
            );
        } catch (Exception ignored) {}
        try {
            restTemplate.exchange(
                    evolutionApiUrl + "/instance/delete/" + instanceName,
                    HttpMethod.DELETE,
                    new HttpEntity(headers),
                    String.class
            );
        } catch (Exception ignored) {}
    }

    @Override
    public String createInstanceAndGetQR(UUID tenantId) {
        log.info("1. Iniciando conexao (Modo QR) para tenant: {}", tenantId);
        HttpHeaders headers = buildHeaders();

        WhatsAppAccountEntity account = findAccountByTenant(tenantId);
        if (account == null) {
            account = new WhatsAppAccountEntity();
        }

        account.setTenantId(tenantId);

        if (account.getInstanceName() != null) {
            deleteSyncInEvolution(account.getInstanceName());
        }

        String newInstanceName = "tenant_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        account.setInstanceName(newInstanceName);
        account.setEvolutionApiKey(globalApiKey);
        account.setDisplayPhoneNumber("Pendente (QR Code)");
        repository.saveAndFlush(account);
        instanceCacheByTenant.put(tenantId, newInstanceName);

        String base64Qr = null;
        log.info("2. Criando NOVA instancia ({}) no Modo QR Code...", newInstanceName);

        try {
            Map createBody = new HashMap();
            createBody.put("instanceName", newInstanceName);
            createBody.put("token", newInstanceName);
            createBody.put("qrcode", true);
            createBody.put("integration", "WHATSAPP-BAILEYS");

            ResponseEntity createResponse = restTemplate.exchange(
                    evolutionApiUrl + "/instance/create",
                    HttpMethod.POST,
                    new HttpEntity(createBody, headers),
                    Map.class
            );

            base64Qr = extrairQrCode(createResponse.getBody());

            try {
                activateBot(tenantId);
            } catch (Exception e) {
                log.warn("Aviso na auto-ativacao do webhook: {}", e.getMessage());
            }
        } catch (ResourceAccessException e) {
            log.error("Evolution API inacessivel em {}: {}", evolutionApiUrl, e.getMessage());
            throw new IllegalStateException("Nao foi possivel conectar a Evolution API em " + evolutionApiUrl + ". Verifique se o Docker esta ligado.");
        } catch (HttpStatusCodeException e) {
            log.error("Erro HTTP da Evolution API ({}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                throw new IllegalStateException("Chave Global da Evolution API invalida (401/403). Verifique 'evolution.api.global-apikey' no application.yml.");
            }
            throw new IllegalStateException("Erro na Evolution API: " + e.getStatusCode());
        } catch (Exception e) {
            log.warn("Aviso ao criar instancia QR: {}", e.getMessage());
        }

        if (base64Qr != null && !base64Qr.isBlank()) {
            log.info("3. QR Code obtido imediatamente na criacao da instancia!");
            return base64Qr;
        }

        log.info("3. Solicitando imagem do QR Code a API...");
        for (int i = 1; i <= 5; i++) {
            try {
                Thread.sleep(1200);
                ResponseEntity connectResponse = restTemplate.exchange(
                        evolutionApiUrl + "/instance/connect/" + newInstanceName,
                        HttpMethod.GET,
                        new HttpEntity(headers),
                        Map.class
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

        throw new IllegalStateException("A Evolution API nao devolveu o QR Code a tempo. Verifique se o container Docker da Evolution API esta online.");
    }

    @Override
    public String getFreshQR(UUID tenantId) {
        WhatsAppAccountEntity account = findAccountByTenant(tenantId);
        if (account == null || account.getInstanceName() == null) {
            throw new IllegalArgumentException("Nenhuma instancia encontrada.");
        }

        ResponseEntity connectResponse = restTemplate.exchange(
                evolutionApiUrl + "/instance/connect/" + account.getInstanceName(),
                HttpMethod.GET,
                new HttpEntity(buildHeaders()),
                Map.class
        );

        String base64Qr = extrairQrCode(connectResponse.getBody());
        if (base64Qr != null && !base64Qr.isBlank()) {
            return base64Qr;
        }

        throw new IllegalStateException("O WhatsApp ja esta conectado ou a imagem ainda esta a ser gerada.");
    }

    @Override
    @Transactional
    public Map getConnectionStatus(UUID tenantId) {
        Map result = new HashMap();
        result.put("connected", false);
        result.put("state", "close");
        result.put("status", "DISCONNECTED");

        if (tenantId == null) return result;

        WhatsAppAccountEntity account = findAccountByTenant(tenantId);

        if (account == null || account.getInstanceName() == null || account.getInstanceName().isBlank()) {
            return result;
        }

        String instanceName = account.getInstanceName();
        result.put("instanceName", instanceName);

        try {
            // 1. Consulta fetchInstances que traz connectionStatus e disconnectionReasonCode SEM CACHE DE 2 MINUTOS!
            String state = null;
            boolean forceDisconnectedByPhone = false;

            try {
                ResponseEntity fetchResp = restTemplate.exchange(
                        evolutionApiUrl + "/instance/fetchInstances?instanceName=" + instanceName,
                        HttpMethod.GET,
                        new HttpEntity(buildHeaders()),
                        Object.class
                );
                Map instDetails = extrairInstanceMapFromFetch(fetchResp.getBody(), instanceName);
                if (instDetails != null) {
                    Object connStatus = instDetails.get("connectionStatus");
                    if (connStatus != null) {
                        state = String.valueOf(connStatus);
                    }
                    Object reasonCode = instDetails.get("disconnectionReasonCode");
                    Object disconnAt = instDetails.get("disconnectionAt");
                    // Se o celular desconectou, disconnectionReasonCode fica 401 ou connectionStatus fica close/refused
                    if ("401".equals(String.valueOf(reasonCode))
                            || "close".equalsIgnoreCase(state)
                            || "refused".equalsIgnoreCase(state)
                            || (disconnAt != null && !"null".equals(String.valueOf(disconnAt)) && !"open".equalsIgnoreCase(state))) {
                        forceDisconnectedByPhone = true;
                        state = "close";
                    }
                }
            } catch (HttpStatusCodeException e) {
                if (e.getStatusCode().value() == 404) {
                    forceDisconnectedByPhone = true;
                    state = "close";
                }
            } catch (Exception ignored) {}

            // 2. Se fetchInstances nao trouxe o estado, consulta connectionState como fallback
            if (state == null) {
                ResponseEntity response = restTemplate.exchange(
                        evolutionApiUrl + "/instance/connectionState/" + instanceName,
                        HttpMethod.GET,
                        new HttpEntity(buildHeaders()),
                        Map.class
                );
                state = extrairState(response.getBody());
            }

            boolean isConnected = !forceDisconnectedByPhone && ("open".equalsIgnoreCase(state) || "connected".equalsIgnoreCase(state));

            if (isConnected) {
                result.put("connected", true);
                result.put("state", "open");
                result.put("status", "CONNECTED");

                if ("Pendente (QR Code)".equals(account.getDisplayPhoneNumber())) {
                    account.setDisplayPhoneNumber("Conectado (" + instanceName + ")");
                    repository.saveAndFlush(account);
                }
            } else {
                boolean wasConnectedBefore = account.getDisplayPhoneNumber() != null
                        && account.getDisplayPhoneNumber().startsWith("Conectado");

                if (wasConnectedBefore && (forceDisconnectedByPhone || "close".equalsIgnoreCase(state) || "refused".equalsIgnoreCase(state))) {
                    log.info("Desconexao pelo celular detectada na hora para instancia {}. Limpando sessao...", instanceName);
                    instanceCacheByTenant.remove(tenantId);
                    repository.delete(account);
                    repository.flush();
                    deleteSyncInEvolution(instanceName);
                    result.put("connected", false);
                    result.put("state", "close");
                    result.put("status", "DISCONNECTED");
                } else {
                    result.put("connected", false);
                    result.put("state", state != null ? state : "close");
                    result.put("status", "CONNECTING");
                }
            }
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 404) {
                instanceCacheByTenant.remove(tenantId);
                repository.delete(account);
                repository.flush();
            }
        } catch (Exception e) {
            log.debug("Instancia {} ainda nao conectada: {}", instanceName, e.getMessage());
        }

        return result;
    }

    @Override
    public void activateBot(UUID tenantId) {
        WhatsAppAccountEntity account = findAccountByTenant(tenantId);
        if (account == null || account.getInstanceName() == null) {
            throw new IllegalArgumentException("Instancia nao encontrada. Conecte o WhatsApp primeiro.");
        }

        String targetWebhookUrl = webhookBaseUrl + "/api/v1/whatsapp/webhook/" + account.getInstanceName();

        Map webhookConfig = new HashMap();
        webhookConfig.put("enabled", true);
        webhookConfig.put("url", targetWebhookUrl);
        webhookConfig.put("byEvents", false);
        webhookConfig.put("base64", false);
        webhookConfig.put("events", List.of("MESSAGES_UPSERT", "CONNECTION_UPDATE"));

        Map webhookBody = new HashMap();
        webhookBody.put("webhook", webhookConfig);

        try {
            restTemplate.exchange(
                    evolutionApiUrl + "/webhook/set/" + account.getInstanceName(),
                    HttpMethod.POST,
                    new HttpEntity(webhookBody, buildHeaders()),
                    Map.class
            );
        } catch (Exception e) {
            restTemplate.exchange(
                    evolutionApiUrl + "/webhook/set/" + account.getInstanceName(),
                    HttpMethod.POST,
                    new HttpEntity(webhookConfig, buildHeaders()),
                    Map.class
            );
        }

        log.info("Webhook ativado com seguranca para a instancia {} -> {}!", account.getInstanceName(), targetWebhookUrl);
    }

    @Override
    @Transactional
    public void logoutInstance(UUID tenantId) {
        log.info("Botao Desconectar acionado para o tenant: {}", tenantId);
        Set instancesToDisconnect = new HashSet();

        Object cached = instanceCacheByTenant.remove(tenantId);
        if (cached instanceof String) {
            instancesToDisconnect.add(String.valueOf(cached));
        }

        List allAccounts = repository.findAll();
        for (Object obj : allAccounts) {
            WhatsAppAccountEntity acc = (WhatsAppAccountEntity) obj;
            if (tenantId == null || tenantId.equals(acc.getTenantId()) || allAccounts.size() == 1) {
                if (acc.getInstanceName() != null && !acc.getInstanceName().isBlank()) {
                    instancesToDisconnect.add(acc.getInstanceName());
                }
                repository.delete(acc);
            }
        }
        repository.flush();

        // Se nao achou no banco mas houver instancia ativa na Evolution API, desconecta tambem
        if (instancesToDisconnect.isEmpty()) {
            try {
                ResponseEntity fetchResp = restTemplate.exchange(
                        evolutionApiUrl + "/instance/fetchInstances",
                        HttpMethod.GET,
                        new HttpEntity(buildHeaders()),
                        List.class
                );
                Object body = fetchResp.getBody();
                if (body instanceof List) {
                    List list = (List) body;
                    for (Object item : list) {
                        if (item instanceof Map) {
                            Map mapItem = (Map) item;
                            Object name = mapItem.get("name") != null ? mapItem.get("name") : mapItem.get("instanceName");
                            if (name != null && String.valueOf(name).startsWith("tenant_")) {
                                instancesToDisconnect.add(String.valueOf(name));
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        for (Object instObj : instancesToDisconnect) {
            disconnectInPhoneAndDeleteAsync(String.valueOf(instObj));
        }

        log.info("Sessoes desconectadas com sucesso: {}", instancesToDisconnect);
    }

    @Override
    public String getPairingCode(UUID tenantId, String phoneNumber) {
        log.info("Iniciando requisicao de Pairing Code para o tenant: {}, numero: {}", tenantId, phoneNumber);
        String cleanNumber = phoneNumber.replaceAll("[^0-9]", "");
        if (cleanNumber.length() <= 9) {
            throw new IllegalArgumentException("Informe o numero completo com DDI + DDD + Numero (Ex: 5511999999999).");
        }

        HttpHeaders headers = buildHeaders();

        WhatsAppAccountEntity account = findAccountByTenant(tenantId);
        if (account == null) {
            account = new WhatsAppAccountEntity();
        }

        account.setTenantId(tenantId);

        if (account.getInstanceName() != null) {
            deleteSyncInEvolution(account.getInstanceName());
        }

        String newInstanceName = "tenant_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        account.setInstanceName(newInstanceName);
        account.setEvolutionApiKey(globalApiKey);
        account.setDisplayPhoneNumber("Pendente (QR Code)");
        repository.saveAndFlush(account);
        instanceCacheByTenant.put(tenantId, newInstanceName);

        log.info("1. Criando NOVA instancia ({}) ja no Modo Numerico...", newInstanceName);
        try {
            Map createBody = new HashMap();
            createBody.put("instanceName", newInstanceName);
            createBody.put("token", newInstanceName);
            createBody.put("qrcode", false);
            createBody.put("number", cleanNumber);
            createBody.put("integration", "WHATSAPP-BAILEYS");

            ResponseEntity createResp = restTemplate.exchange(
                    evolutionApiUrl + "/instance/create",
                    HttpMethod.POST,
                    new HttpEntity(createBody, headers),
                    Map.class
            );

            String immediateCode = extrairPairingCode(createResp.getBody());
            try {
                activateBot(tenantId);
            } catch (Exception e) {
                log.warn("Aviso na auto-ativacao do webhook: {}", e.getMessage());
            }

            if (immediateCode != null) {
                log.info("Pairing code gerado imediatamente na criacao: {}", immediateCode);
                return immediateCode;
            }
        } catch (ResourceAccessException e) {
            log.error("Evolution API offline: ", e);
            throw new IllegalStateException("Nao foi possivel conectar a Evolution API em " + evolutionApiUrl + ". Verifique se o Docker esta ligado.");
        } catch (HttpStatusCodeException e) {
            log.error("Erro HTTP ao criar instancia ({}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                throw new IllegalStateException("Chave Global da Evolution API invalida (401/403). Verifique o application.yml.");
            }
            throw new IllegalStateException("Falha na Evolution API: " + e.getStatusCode());
        } catch (Exception e) {
            log.error("Erro ao criar nova instancia: ", e);
            throw new IllegalStateException("Falha ao criar o motor do WhatsApp. Verifique se o Docker esta ligado.");
        }

        log.info("2. Solicitando o codigo de emparelhamento final a API...");
        String url = evolutionApiUrl + "/instance/connect/" + newInstanceName + "?number=" + cleanNumber;

        for (int i = 1; i <= 5; i++) {
            try {
                Thread.sleep(1200);
                ResponseEntity response = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        new HttpEntity(headers),
                        Map.class
                );

                String code = extrairPairingCode(response.getBody());
                if (code != null) {
                    log.info("Pairing code gerado com sucesso: {}", code);
                    return code;
                }

                log.info("Tentativa {}/5: Motor Baileys ainda a inicializar...", i);
            } catch (Exception e) {
                log.warn("Tentativa {}/5 falhou: {}", i, e.getMessage());
            }
        }

        throw new IllegalStateException("Nao foi possivel gerar o codigo de pareamento para este numero no momento. Tente via QR Code.");
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", globalApiKey);
        return headers;
    }

    private Map extrairInstanceMapFromFetch(Object rawBody, String targetInstance) {
        if (rawBody instanceof List) {
            List list = (List) rawBody;
            for (Object item : list) {
                if (item instanceof Map) {
                    Map map = (Map) item;
                    Object name = map.get("name") != null ? map.get("name") : map.get("instanceName");
                    if (targetInstance.equals(String.valueOf(name))) {
                        return map;
                    }
                    if (map.get("instance") instanceof Map) {
                        Map inner = (Map) map.get("instance");
                        Object innerName = inner.get("instanceName") != null ? inner.get("instanceName") : inner.get("name");
                        if (targetInstance.equals(String.valueOf(innerName))) {
                            return inner;
                        }
                    }
                }
            }
        } else if (rawBody instanceof Map) {
            return (Map) rawBody;
        }
        return null;
    }

    private String extrairState(Object rawBody) {
        if (!(rawBody instanceof Map)) return null;
        Map body = (Map) rawBody;
        if (body.containsKey("state") && body.get("state") != null) {
            return String.valueOf(body.get("state"));
        }
        if (body.containsKey("connectionStatus") && body.get("connectionStatus") != null) {
            return String.valueOf(body.get("connectionStatus"));
        }
        if (body.containsKey("instance") && body.get("instance") instanceof Map) {
            return extrairState(body.get("instance"));
        }
        if (body.containsKey("data") && body.get("data") instanceof Map) {
            return extrairState(body.get("data"));
        }
        return null;
    }

    private String extrairPairingCode(Object rawBody) {
        if (!(rawBody instanceof Map)) return null;
        Map body = (Map) rawBody;

        if (body.containsKey("pairingCode") && body.get("pairingCode") != null) {
            String code = String.valueOf(body.get("pairingCode")).trim();
            if (!code.isBlank() && !code.equals("null")) return code;
        }
        if (body.containsKey("code") && body.get("code") != null) {
            String code = String.valueOf(body.get("code")).trim();
            if (!code.isBlank() && !code.equals("null") && code.length() <= 12 && !code.contains("@") && !code.contains(",")) {
                return code;
            }
        }
        if (body.containsKey("qrcode") && body.get("qrcode") instanceof Map) {
            String nested = extrairPairingCode(body.get("qrcode"));
            if (nested != null) return nested;
        }
        if (body.containsKey("data") && body.get("data") instanceof Map) {
            String nested = extrairPairingCode(body.get("data"));
            if (nested != null) return nested;
        }
        return null;
    }

    private String extrairQrCode(Object rawBody) {
        if (!(rawBody instanceof Map)) return null;
        Map body = (Map) rawBody;

        if (body.containsKey("base64") && body.get("base64") instanceof String) {
            String b64 = (String) body.get("base64");
            if (!b64.isBlank()) return b64;
        }
        if (body.containsKey("qrcode")) {
            Object qrObj = body.get("qrcode");
            if (qrObj instanceof Map) {
                String nested = extrairQrCode(qrObj);
                if (nested != null) return nested;
            } else if (qrObj instanceof String) {
                String qrString = (String) qrObj;
                if (qrString.startsWith("data:image")) return qrString;
            }
        }
        if (body.containsKey("data") && body.get("data") instanceof Map) {
            String nested = extrairQrCode(body.get("data"));
            if (nested != null) return nested;
        }
        if (body.containsKey("instance") && body.get("instance") instanceof Map) {
            String nested = extrairQrCode(body.get("instance"));
            if (nested != null) return nested;
        }
        return null;
    }
}