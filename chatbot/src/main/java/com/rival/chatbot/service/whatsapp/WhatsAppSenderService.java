package com.rival.chatbot.service.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class WhatsAppSenderService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppSenderService.class);
    private final RestTemplate restTemplate;

    // A URL onde a Evolution API vai rodar
    @Value("${evolution.api.url:http://localhost:8081}")
    private String evolutionApiUrl;

    public WhatsAppSenderService() {
        this.restTemplate = new RestTemplate();
    }

    public void sendMessage(String instanceName, String apikey, String toPhoneNumber, String messageText) {
        if (messageText == null || messageText.isBlank()) return;

        // Rota da Evolution API para envio de texto
        String url = evolutionApiUrl + "/message/sendText/" + instanceName;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", apikey);

        // Correção do Payload para a Evolution API V2
        Map<String, Object> body = new HashMap<>();
        body.put("number", toPhoneNumber);
        body.put("text", messageText); // A propriedade TEM de se chamar "text" diretamente na raiz

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            log.info("Mensagem enviada com sucesso via Evolution API para {}. Status: {}", toPhoneNumber, response.getStatusCode());
        } catch (ResourceAccessException e) {
            log.warn("Evolution API offline (Porta 8081). MENSAGEM SIMULADA para {}: \"{}\"", toPhoneNumber, messageText);
        } catch (Exception e) {
            log.error("Erro inesperado ao enviar mensagem via Evolution API: " + e.getMessage());
        }
    }
}