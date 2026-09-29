package com.rival.chatbot.service.whatsapp.impl;

import com.rival.chatbot.domain.whatsapp.WhatsAppAccountEntity;
import com.rival.chatbot.dto.ChatRequestDTO;
import com.rival.chatbot.dto.ChatResponseDTO;
import com.rival.chatbot.repository.CustomerDataRepository;
import com.rival.chatbot.repository.whatsapp.WhatsAppAccountRepository;
import com.rival.chatbot.service.ChatService;
import com.rival.chatbot.service.whatsapp.WhatsAppSenderService;
import com.rival.chatbot.service.whatsapp.WhatsAppWebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class WhatsAppWebhookServiceImpl implements WhatsAppWebhookService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookServiceImpl.class);

    private final ChatService chatService;
    private final WhatsAppSenderService whatsAppSenderService;
    private final WhatsAppAccountRepository whatsAppAccountRepository;
    private final CustomerDataRepository customerDataRepository;

    // ✅ Para chamar a Evolution API e baixar a mídia
    @Value("${evolution.api.url:http://localhost:8081}")
    private String evolutionApiUrl;
    private final RestTemplate restTemplate = new RestTemplate();

    public WhatsAppWebhookServiceImpl(ChatService chatService,
                                      WhatsAppSenderService whatsAppSenderService,
                                      WhatsAppAccountRepository whatsAppAccountRepository,
                                      CustomerDataRepository customerDataRepository) {
        this.chatService = chatService;
        this.whatsAppSenderService = whatsAppSenderService;
        this.whatsAppAccountRepository = whatsAppAccountRepository;
        this.customerDataRepository = customerDataRepository;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void processWebhookPayload(String instanceName, Map<String, Object> payload) {
        try {
            if (!"messages.upsert".equals(payload.get("event"))) {
                return;
            }

            Map<String, Object> data = (Map<String, Object>) payload.get("data");
            if (data == null) return;

            Map<String, Object> msgObject = data.containsKey("message") ? (Map<String, Object>) data.get("message") : data;

            String userPhoneNumber = extractPhoneNumber(data, msgObject);
            Boolean fromMe = extractFromMe(data, msgObject);

            if (userPhoneNumber == null || userPhoneNumber.contains("@g.us") || Boolean.TRUE.equals(fromMe)) {
                return;
            }

            WhatsAppAccountEntity account = whatsAppAccountRepository.findByInstanceName(instanceName)
                    .orElseThrow(() -> new IllegalArgumentException("Instância não encontrada no banco: " + instanceName));

            // 1. Extrai Texto
            String userText = extractUserText(msgObject);

            // ✅ 2. Extrai Mídia (Se houver, baixa o arquivo e gera um Link Local)
            String mediaUrl = extractAndSaveMedia(instanceName, account.getEvolutionApiKey(), msgObject);

            // 3. Junta tudo para o Angular processar no Pipe
            String finalContent = userText != null ? userText : "";
            if (mediaUrl != null) {
                finalContent = finalContent.isEmpty() ? mediaUrl : finalContent + "\n" + mediaUrl;
            }

            // Ignora se for uma mensagem de sistema vazia
            if (finalContent.isEmpty()) {
                return;
            }

            log.info("Mensagem WhatsApp recebida -> De: {}, Conteúdo: '{}'", userPhoneNumber, finalContent);
            userPhoneNumber = userPhoneNumber.split("@")[0];

            UUID sessionId = UUID.nameUUIDFromBytes(userPhoneNumber.getBytes());
            ChatRequestDTO chatRequest = new ChatRequestDTO(sessionId, account.getTenantId(), finalContent, "WHATSAPP", userPhoneNumber);

            ChatResponseDTO response = chatService.processFlowMessage(chatRequest);

            // CAPTURA AUTOMÁTICA DO NOME DO WHATSAPP (PUSHNAME)
            String pushName = data.containsKey("pushName") ? (String) data.get("pushName") : null;
            if (pushName != null && !pushName.isBlank()) {
                customerDataRepository.findBySessionId(sessionId).ifPresent(customer -> {
                    if (customer.getName() == null) {
                        customer.setName(pushName);
                        customerDataRepository.save(customer);
                    }
                });
            }

            whatsAppSenderService.sendMessage(
                    account.getInstanceName(),
                    account.getEvolutionApiKey(),
                    userPhoneNumber,
                    response.response()
            );

        } catch (Exception e) {
            log.error("Erro interno ao processar Webhook da Evolution API: ", e);
        }
    }

    // ✅ NOVO MÉTODO: Faz o download da mídia da Evolution API e salva localmente
    @SuppressWarnings("unchecked")
    private String extractAndSaveMedia(String instanceName, String apiKey, Map<String, Object> msgObject) {
        try {
            Map<String, Object> messageContent = (Map<String, Object>) msgObject.get("message");
            if (messageContent == null) return null;

            String mediaType = null;
            String extension = ".bin";

            if (messageContent.containsKey("imageMessage")) { mediaType = "image"; extension = ".jpg"; }
            else if (messageContent.containsKey("videoMessage")) { mediaType = "video"; extension = ".mp4"; }
            else if (messageContent.containsKey("documentMessage")) { mediaType = "document"; extension = ".pdf"; }
            else if (messageContent.containsKey("stickerMessage")) { mediaType = "sticker"; extension = ".webp"; }

            // Se não for mídia reconhecida, ignora
            if (mediaType == null) return null;

            log.info("Mídia detectada ({}). A baixar da Evolution API...", mediaType);

            String url = evolutionApiUrl + "/chat/getBase64FromMediaMessage/" + instanceName;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("apikey", apiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("message", msgObject);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            if (response.getBody() != null && response.getBody().containsKey("base64")) {
                String base64Data = (String) response.getBody().get("base64");
                if (base64Data == null || base64Data.isBlank()) return null;

                // Limpa o prefixo do base64 (ex: "data:image/jpeg;base64,...")
                if (base64Data.contains(",")) {
                    base64Data = base64Data.split(",")[1];
                }

                // Salva o ficheiro na pasta uploads
                byte[] decodedBytes = java.util.Base64.getDecoder().decode(base64Data);
                File dir = new File("uploads");
                if (!dir.exists()) dir.mkdirs();

                String fileName = "wa_" + UUID.randomUUID().toString().substring(0, 8) + extension;
                File file = new File(dir, fileName);

                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                    fos.write(decodedBytes);
                }

                log.info("Arquivo WhatsApp salvo com sucesso: {}", fileName);

                // Devolve a URL que o Angular vai conseguir ler
                return "http://localhost:8080/uploads/" + fileName;
            }
        } catch (Exception e) {
            log.error("Erro ao tentar baixar mídia do WhatsApp", e);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String extractPhoneNumber(Map<String, Object> data, Map<String, Object> msgObject) {
        if (data.containsKey("key")) {
            return (String) ((Map<String, Object>) data.get("key")).get("remoteJid");
        } else if (msgObject.containsKey("key")) {
            return (String) ((Map<String, Object>) msgObject.get("key")).get("remoteJid");
        }
        return (String) data.get("remoteJid");
    }

    @SuppressWarnings("unchecked")
    private Boolean extractFromMe(Map<String, Object> data, Map<String, Object> msgObject) {
        if (data.containsKey("key")) {
            return (Boolean) ((Map<String, Object>) data.get("key")).get("fromMe");
        } else if (msgObject.containsKey("key")) {
            return (Boolean) ((Map<String, Object>) msgObject.get("key")).get("fromMe");
        }
        return (Boolean) data.get("fromMe");
    }

    @SuppressWarnings("unchecked")
    private String extractUserText(Map<String, Object> msgObject) {
        if (msgObject == null) return null;

        Map<String, Object> messageContent = msgObject;
        if (msgObject.containsKey("message") && msgObject.get("message") instanceof Map) {
            messageContent = (Map<String, Object>) msgObject.get("message");
        }

        // 1. Mensagem de texto simples
        if (messageContent.containsKey("conversation")) {
            return (String) messageContent.get("conversation");
        }

        // 2. Mensagem longa, resposta a outra mensagem, ou WhatsApp Web (Bug corrigido)
        if (messageContent.containsKey("extendedTextMessage")) {
            Object extText = messageContent.get("extendedTextMessage");
            if (extText instanceof Map) {
                return (String) ((Map<String, Object>) extText).get("text");
            }
        }

        // 3. Texto que vem na legenda de uma imagem
        if (messageContent.containsKey("imageMessage")) {
            Object imgMsg = messageContent.get("imageMessage");
            if (imgMsg instanceof Map && ((Map<?, ?>) imgMsg).containsKey("caption")) {
                return (String) ((Map<String, Object>) imgMsg).get("caption");
            }
        }
        if (messageContent.containsKey("videoMessage")) {
            Object vidMsg = messageContent.get("videoMessage");
            if (vidMsg instanceof Map && ((Map<?, ?>) vidMsg).containsKey("caption")) {
                return (String) ((Map<String, Object>) vidMsg).get("caption");
            }
        }
        return null;
    }
}