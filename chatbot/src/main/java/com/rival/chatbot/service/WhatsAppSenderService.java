package com.rival.chatbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class WhatsAppSenderService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppSenderService.class);
    private final RestTemplate restTemplate = new RestTemplate();

    public void sendMessage(String phoneNumberId, String apiToken, String toPhoneNumber, String messageText) {
        String url = "https://graph.facebook.com/v20.0/" + phoneNumberId + "/messages";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiToken);

        Map<String, Object> body = new HashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("to", toPhoneNumber);
        body.put("type", "text");

        Map<String, String> text = new HashMap<>();
        text.put("body", messageText);
        body.put("text", text);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            restTemplate.postForEntity(url, request, String.class);
            log.info("Mensagem enviada com sucesso via WhatsApp para o número destinatário {}", toPhoneNumber);
        } catch (Exception e) {
            log.error("Erro ao enviar mensagem dinamicamente para o número {}", toPhoneNumber, e);
        }
    }
}