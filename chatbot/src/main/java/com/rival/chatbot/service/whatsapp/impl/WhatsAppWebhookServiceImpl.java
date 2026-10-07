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

    // Para chamar a Evolution API e baixar a mídia
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
            if (!"messages.upsert".equals(payload.get("event"))) return;

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

            String userText = extractUserText(msgObject);
            String mediaUrl = extractAndSaveMedia(instanceName, account.getEvolutionApiKey(), msgObject);

            String finalContent = userText != null ? userText : "";
            if (mediaUrl != null) {
                finalContent = finalContent.isEmpty() ? mediaUrl : finalContent + "\n" + mediaUrl;
            }

            if (finalContent.isEmpty()) return;

            log.info("Mensagem WhatsApp recebida -> De: {}, Conteúdo: '{}'", userPhoneNumber, finalContent);

            userPhoneNumber = userPhoneNumber.split("@")[0];
            UUID sessionId = UUID.nameUUIDFromBytes(userPhoneNumber.getBytes());

            ChatRequestDTO chatRequest = new ChatRequestDTO(sessionId, account.getTenantId(), finalContent, "WHATSAPP", userPhoneNumber);
            ChatResponseDTO response = chatService.processFlowMessage(chatRequest);

            String pushName = data.containsKey("pushName") ? (String) data.get("pushName") : null;
            if (pushName != null && !pushName.isBlank()) {
                customerDataRepository.findBySessionId(sessionId).ifPresent(customer -> {
                    if (customer.getName() == null) {
                        customer.setName(pushName);
                        customerDataRepository.save(customer);
                    }
                });
            }

            // CORREÇÃO CRÍTICA: Enviar mediante URL se estiver na nuvem (Cloudinary) ou ficheiro local
            if (response != null) {
                String respText = response.response();

                if (respText != null && (respText.contains("|||") || respText.startsWith("TEXT:") || respText.startsWith("AUDIO:") || respText.startsWith("VIDEO:"))) {
                    String[] parts = respText.split("\\|\\|\\|");
                    for (String part : parts) {
                        if (part.startsWith("AUDIO:") || part.startsWith("VIDEO:")) {
                            String url = part.substring(6).trim();
                            String mime = part.startsWith("AUDIO:") ? "audio/webm" : "video/mp4"; // Força o MIME Type correto

                            if (url.startsWith("http")) {
                                // Manda diretamente da Nuvem (Cloudinary)
                                whatsAppSenderService.sendMediaMessageFromUrl(account.getInstanceName(), account.getEvolutionApiKey(), userPhoneNumber, "", url, mime);
                            } else {
                                // Manda do disco local
                                File mediaFile = getLocalFileFromUrl(url);
                                if (mediaFile != null && mediaFile.exists()) {
                                    whatsAppSenderService.sendMediaMessage(account.getInstanceName(), account.getEvolutionApiKey(), userPhoneNumber, "", mediaFile);
                                }
                            }
                        } else if (part.startsWith("TEXT:")) {
                            whatsAppSenderService.sendMessage(account.getInstanceName(), account.getEvolutionApiKey(), userPhoneNumber, part.substring(5).trim());
                        }
                    }
                } else {
                    // IA / MENSAGENS COMUNS (Modo Legado)
                    if (response.audioUrl() != null && !response.audioUrl().isBlank()) {
                        if (response.audioUrl().startsWith("http")) {
                            whatsAppSenderService.sendMediaMessageFromUrl(account.getInstanceName(), account.getEvolutionApiKey(), userPhoneNumber, "", response.audioUrl(), "audio/webm");
                        } else {
                            File audioFile = getLocalFileFromUrl(response.audioUrl());
                            if (audioFile != null && audioFile.exists()) {
                                whatsAppSenderService.sendMediaMessage(account.getInstanceName(), account.getEvolutionApiKey(), userPhoneNumber, "", audioFile);
                            }
                        }
                    }
                    if (response.imageUrl() != null && !response.imageUrl().isBlank()) {
                        if (response.imageUrl().startsWith("http")) {
                            whatsAppSenderService.sendMediaMessageFromUrl(account.getInstanceName(), account.getEvolutionApiKey(), userPhoneNumber, "", response.imageUrl(), "image/jpeg");
                        } else {
                            File mediaFile = getLocalFileFromUrl(response.imageUrl());
                            if (mediaFile != null && mediaFile.exists()) {
                                whatsAppSenderService.sendMediaMessage(account.getInstanceName(), account.getEvolutionApiKey(), userPhoneNumber, "", mediaFile);
                            }
                        }
                    }
                    if (respText != null && !respText.isBlank()) {
                        whatsAppSenderService.sendMessage(account.getInstanceName(), account.getEvolutionApiKey(), userPhoneNumber, respText);
                    }
                }
            }

        } catch (Exception e) {
            log.error("Erro interno ao processar Webhook da Evolution API: ", e);
        }
    }

    private File getLocalFileFromUrl(String url) {
        try {
            String fileName = url.substring(url.lastIndexOf("/") + 1);
            String userDir = System.getProperty("user.dir");
            return new File(userDir + File.separator + "uploads", fileName);
        } catch (Exception e) {
            return null;
        }
    }

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
            else if (messageContent.containsKey("audioMessage")) {
                mediaType = "audio";
                extension = "_voz.ogg";
            }

            if (mediaType == null) return null;

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

                if (base64Data.contains(",")) base64Data = base64Data.split(",")[1];

                byte[] decodedBytes = java.util.Base64.getDecoder().decode(base64Data);

                File dir = new File("uploads");
                if (!dir.exists()) dir.mkdirs();

                String fileName = "wa_" + UUID.randomUUID().toString().substring(0, 8) + extension;
                File file = new File(dir, fileName);

                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                    fos.write(decodedBytes);
                }

                return "http://localhost:8080/uploads/" + fileName;
            }
        } catch (Exception e) {
            log.error("Erro ao tentar baixar mídia do WhatsApp", e);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String extractPhoneNumber(Map<String, Object> data, Map<String, Object> msgObject) {
        if (data.containsKey("key")) return (String) ((Map<String, Object>) data.get("key")).get("remoteJid");
        else if (msgObject.containsKey("key")) return (String) ((Map<String, Object>) msgObject.get("key")).get("remoteJid");
        return (String) data.get("remoteJid");
    }

    @SuppressWarnings("unchecked")
    private Boolean extractFromMe(Map<String, Object> data, Map<String, Object> msgObject) {
        if (data.containsKey("key")) return (Boolean) ((Map<String, Object>) data.get("key")).get("fromMe");
        else if (msgObject.containsKey("key")) return (Boolean) ((Map<String, Object>) msgObject.get("key")).get("fromMe");
        return (Boolean) data.get("fromMe");
    }

    @SuppressWarnings("unchecked")
    private String extractUserText(Map<String, Object> msgObject) {
        if (msgObject == null) return null;

        Map<String, Object> messageContent = msgObject;
        if (msgObject.containsKey("message") && msgObject.get("message") instanceof Map) {
            messageContent = (Map<String, Object>) msgObject.get("message");
        }

        if (messageContent.containsKey("conversation")) return (String) messageContent.get("conversation");

        if (messageContent.containsKey("extendedTextMessage")) {
            Object extText = messageContent.get("extendedTextMessage");
            if (extText instanceof Map) return (String) ((Map<String, Object>) extText).get("text");
        }

        if (messageContent.containsKey("imageMessage")) {
            Object imgMsg = messageContent.get("imageMessage");
            if (imgMsg instanceof Map && ((Map<?, ?>) imgMsg).containsKey("caption")) return (String) ((Map<String, Object>) imgMsg).get("caption");
        }

        if (messageContent.containsKey("videoMessage")) {
            Object vidMsg = messageContent.get("videoMessage");
            if (vidMsg instanceof Map && ((Map<?, ?>) vidMsg).containsKey("caption")) return (String) ((Map<String, Object>) vidMsg).get("caption");
        }

        if (messageContent.containsKey("documentMessage")) {
            Object docMsg = messageContent.get("documentMessage");
            if (docMsg instanceof Map && ((Map<?, ?>) docMsg).containsKey("caption")) return (String) ((Map<String, Object>) docMsg).get("caption");
        }

        return null;
    }
}