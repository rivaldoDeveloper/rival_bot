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

    /**
     * Transcrição Real e Inteligente usando a sua API.
     */
    public String transcribeAudioFile(File audioFile) {
        if (audioFile == null || !audioFile.exists()) {
            log.warn("Arquivo de áudio inválido fornecido para transcrição.");
            return "";
        }

        if (apiKey == null || apiKey.isBlank() || apiKey.contains("chave_ficticia")) {
            log.error("ERRO: Nenhuma chave de API válida foi configurada no application.yml!");
            return "Erro: Chave de API não configurada.";
        }

        try {
            log.info("Lendo áudio real [{}] e enviando para o motor de transcrição inteligente...", audioFile.getName());
            byte[] audioBytes = Files.readAllBytes(audioFile.toPath());
            String base64Audio = Base64.getEncoder().encodeToString(audioBytes);

            String name = audioFile.getName().toLowerCase();
            String mimeType = "audio/ogg";
            if (name.endsWith(".m4a") || name.endsWith(".mp4")) mimeType = "audio/mp4";
            else if (name.endsWith(".mp3")) mimeType = "audio/mp3";
            else if (name.endsWith(".wav")) mimeType = "audio/wav";

            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(
                            Map.of(
                                    "parts", List.of(
                                            Map.of("text", "Transcreva exatamente o que é falado neste áudio em português. Retorne APENAS o texto transcrito, sem introduções."),
                                            Map.of("inlineData", Map.of("mimeType", mimeType, "data", base64Audio))
                                    )
                            )
                    )
            );

            // Usa o modelo v1beta flash para processamento rápido de áudio
            return callGenerativeApi("/models/gemini-1.5-flash:generateContent?key=" + apiKey, requestBody);

        } catch (Exception e) {
            log.error("Erro no fluxo de transcrição de áudio: ", e);
            return "";
        }
    }

    private String callGenerativeApi(String endpoint, Map<String, Object> requestBody) {
        try {
            Map<?, ?> response = restClient.post()
                    .uri(endpoint)
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
                        log.info("Áudio transcrito com inteligência: '{}'", transcribed);
                        return transcribed.trim();
                    }
                }
            }
        } catch (Exception e) {
            log.error("Falha na chamada da API de transcrição: {}", e.getMessage());
        }
        return "";
    }
}