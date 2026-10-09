package com.rival.chatbot.service.meta;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class MetaSenderService {

    private static final Logger log = LoggerFactory.getLogger(MetaSenderService.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private static final String META_API_URL = "https://graph.facebook.com/v19.0/me/messages";

    @Async("taskExecutor")
    public void sendMessage(String pageAccessToken, String recipientId, String text) {
        if (text == null || text.isBlank()) return;

        Map<String, Object> messageData = new HashMap<>();
        messageData.put("text", text);
        sendToMeta(pageAccessToken, recipientId, messageData);
    }

    @Async("taskExecutor")
    public void sendMediaMessage(String pageAccessToken, String recipientId, String mediaType, String mediaUrl) {
        if (mediaUrl == null || mediaUrl.isBlank()) return;

        // Meta aceita "image", "audio", "video", "file"
        String type = mediaType.toLowerCase();
        if (type.equals("document")) type = "file";

        Map<String, Object> payload = new HashMap<>();
        payload.put("url", mediaUrl);
        payload.put("is_reusable", true);

        Map<String, Object> attachment = new HashMap<>();
        attachment.put("type", type);
        attachment.put("payload", payload);

        Map<String, Object> messageData = new HashMap<>();
        messageData.put("attachment", attachment);

        sendToMeta(pageAccessToken, recipientId, messageData);
    }

    private void sendToMeta(String pageAccessToken, String recipientId, Map<String, Object> messageData) {
        String url = META_API_URL + "?access_token=" + pageAccessToken;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> recipient = new HashMap<>();
        recipient.put("id", recipientId);

        Map<String, Object> body = new HashMap<>();
        body.put("recipient", recipient);
        body.put("message", messageData);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            log.info("Mensagem enviada via Meta API. Status: {}", response.getStatusCode());
        } catch (Exception e) {
            log.error("Erro ao enviar mensagem para a Meta (Facebook/Instagram)", e);
        }
    }
}