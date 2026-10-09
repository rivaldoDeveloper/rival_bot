package com.rival.chatbot.service.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
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

    @Async("taskExecutor")
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
            log.info("Mensagem de texto enviada via Evolution API. Status: {}", response.getStatusCode());
        } catch (ResourceAccessException e) {
            log.warn("Evolution API offline. MENSAGEM SIMULADA para {}: \"{}\"", toPhoneNumber, messageText);
        } catch (Exception e) {
            log.error("Erro inesperado ao enviar mensagem", e);
        }
    }

    @Async("taskExecutor")
    public void sendMediaMessageFromUrl(String instanceName, String apikey, String toPhoneNumber, String caption, String urlStr, String mimeType) {
        try {
            boolean isAudio = mimeType != null && (mimeType.startsWith("audio") || urlStr.endsWith(".ogg") || urlStr.endsWith(".mp3") || urlStr.endsWith(".webm"));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("apikey", apikey);

            Map<String, Object> body = new HashMap<>();
            body.put("number", toPhoneNumber);

            String endpoint;

            if (isAudio) {
                endpoint = evolutionApiUrl + "/message/sendWhatsAppAudio/" + instanceName;
                body.put("audio", urlStr);
            } else {
                endpoint = evolutionApiUrl + "/message/sendMedia/" + instanceName;
                String mediaType = "document";
                if (mimeType != null) {
                    if (mimeType.startsWith("image")) mediaType = "image";
                    else if (mimeType.startsWith("video")) mediaType = "video";
                } else if (urlStr.endsWith(".mp4")) {
                    mediaType = "video";
                } else if (urlStr.endsWith(".jpg") || urlStr.endsWith(".png")) {
                    mediaType = "image";
                }
                body.put("mediatype", mediaType);
                body.put("media", urlStr);
                if (caption != null && !caption.isBlank()) body.put("caption", caption);
            }

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(endpoint, request, String.class);
            log.info("Mídia via URL enviada com sucesso para o WhatsApp.");
        } catch (Exception e) {
            log.error("Erro ao enviar mídia por URL via WhatsApp", e);
        }
    }

    @Async("taskExecutor")
    public void sendMediaMessage(String instanceName, String apikey, String toPhoneNumber, String caption, File file) {
        try {
            String fileName = file.getName().toLowerCase();
            String mimeType = Files.probeContentType(file.toPath());
            if (mimeType == null) mimeType = "application/octet-stream";

            boolean isAudio = mimeType.startsWith("audio") || fileName.contains("gravacao_audio") || fileName.endsWith(".ogg") || fileName.endsWith(".mp3") || fileName.endsWith(".webm") || fileName.endsWith(".m4a");
            byte[] fileContent = Files.readAllBytes(file.toPath());
            String base64 = Base64.getEncoder().encodeToString(fileContent);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("apikey", apikey);

            Map<String, Object> body = new HashMap<>();
            body.put("number", toPhoneNumber);

            String url;
            if (isAudio) {
                url = evolutionApiUrl + "/message/sendWhatsAppAudio/" + instanceName;
                body.put("audio", base64);
                // Força o mimetype de conversão perfeito na Evolution API
                body.put("mimetype", "audio/mp4");
            } else {
                url = evolutionApiUrl + "/message/sendMedia/" + instanceName;
                String mediaType = "document";
                if (mimeType.startsWith("image") || fileName.endsWith(".jpg") || fileName.endsWith(".png")) mediaType = "image";
                else if (mimeType.startsWith("video") || fileName.endsWith(".mp4")) mediaType = "video";

                body.put("mediatype", mediaType);
                body.put("mimetype", mimeType);
                body.put("media", base64);
                if (caption != null && !caption.isBlank()) {
                    body.put("caption", caption);
                }
            }

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(url, request, String.class);
            log.info("Mídia local enviada via WhatsApp.");
        } catch (Exception e) {
            log.error("Erro ao enviar mídia via WhatsApp", e);
        }
    }
}