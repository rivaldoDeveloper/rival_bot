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

import java.io.File;
import java.nio.file.Files;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class WhatsAppSenderService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppSenderService.class);
    private final RestTemplate restTemplate;

    @Value("${evolution.api.url:http://localhost:8081}")
    private String evolutionApiUrl;

    public WhatsAppSenderService() {
        this.restTemplate = new RestTemplate();
    }

    public void sendMessage(String instanceName, String apikey, String toPhoneNumber, String messageText) {
        if (messageText == null || messageText.isBlank()) return;

        String url = evolutionApiUrl + "/message/sendText/" + instanceName;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", apikey);

        Map<String, Object> body = new HashMap<>();
        body.put("number", toPhoneNumber);
        body.put("text", messageText);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            log.info("Mensagem de texto enviada com sucesso via Evolution API. Status: {}", response.getStatusCode());
        } catch (ResourceAccessException e) {
            log.warn("Evolution API offline. MENSAGEM SIMULADA para {}: \"{}\"", toPhoneNumber, messageText);
        } catch (Exception e) {
            log.error("Erro inesperado ao enviar mensagem via Evolution API", e);
        }
    }

    // NUEVO MÉTODO: Envía medios a WhatsApp mediante Evolution API
    public void sendMediaMessage(String instanceName, String apikey, String toPhoneNumber, String caption, File file) {
        try {
            String url = evolutionApiUrl + "/message/sendMedia/" + instanceName;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("apikey", apikey);

            // Determinar Mimetype y Tipo de Medio
            String mimeType = Files.probeContentType(file.toPath());
            if (mimeType == null) mimeType = "application/octet-stream";

            String mediaType = "document";
            if (mimeType.startsWith("image")) mediaType = "image";
            else if (mimeType.startsWith("video")) mediaType = "video";
            else if (mimeType.startsWith("audio")) mediaType = "audio";

            // Convertir archivo a Base64 para el Payload
            byte[] fileContent = Files.readAllBytes(file.toPath());
            String base64 = Base64.getEncoder().encodeToString(fileContent);

            Map<String, Object> body = new HashMap<>();
            body.put("number", toPhoneNumber);
            body.put("mediatype", mediaType);
            body.put("mimetype", mimeType);
            body.put("media", base64);

            if (caption != null && !caption.isBlank()) {
                body.put("caption", caption);
            }

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            log.info("Mídia enviada via Evolution API. Status: {}", response.getStatusCode());

        } catch (Exception e) {
            log.error("Erro ao enviar mídia via WhatsApp", e);
        }
    }
}