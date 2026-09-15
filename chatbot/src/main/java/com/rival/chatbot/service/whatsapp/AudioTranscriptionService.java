package com.rival.chatbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.File;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class AudioTranscriptionService {

    private static final Logger log = LoggerFactory.getLogger(AudioTranscriptionService.class);
    private final RestClient restClient;
    private final String apiKey;

    public AudioTranscriptionService(@Value("${spring.ai.openai.api-key:}") String apiKey) {
        this.apiKey = apiKey;
        this.restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public String transcribeAudioFile(File audioFile) {
        if (audioFile == null || !audioFile.exists()) {
            log.warn("Arquivo de áudio inválido fornecido para transcrição.");
            return "";
        }

        try {
            log.info("Iniciando transcrição de áudio via Gemini Multimodal: {}", audioFile.getName());

            byte[] audioBytes = Files.readAllBytes(audioFile.toPath());
            String base64Audio = Base64.getEncoder().encodeToString(audioBytes);

            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(
                            Map.of(
                                    "parts", List.of(
                                            Map.of("text", "Transcreva exatamente o que é falado neste áudio em português. Retorne APENAS o texto transcrito, sem introduções."),
                                            Map.of("inlineData", Map.of(
                                                    "mimeType", "audio/ogg",
                                                    "data", base64Audio
                                            ))
                                    )
                            )
                    )
            );

            // Atualizado o modelo para gemini-2.5-flash suportado pela API v1beta
            Map<?, ?> response = restClient.post()
                    .uri("/models/gemini-2.5-flash:generateContent?key=" + apiKey)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.containsKey("candidates")) {
                List<?> candidates = (List<?>) response.get("candidates");
                if (!candidates.isEmpty()) {
                    Map<?, ?> firstCandidate = (Map<?, ?>) candidates.get(0);
                    Map<?, ?> content = (Map<?, ?>) firstCandidate.get("content");
                    List<?> parts = (List<?>) content.get("parts");
                    if (!parts.isEmpty()) {
                        Map<?, ?> firstPart = (Map<?, ?>) parts.get(0);
                        String transcribed = (String) firstPart.get("text");
                        log.info("Áudio transcrito com sucesso pelo Gemini: '{}'", transcribed);
                        return transcribed.trim();
                    }
                }
            }

            return "";
        } catch (Exception e) {
            log.error("Erro ao transcrever arquivo de áudio no Gemini", e);
            return "";
        }
    }
}