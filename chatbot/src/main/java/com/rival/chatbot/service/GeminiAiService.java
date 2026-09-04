package com.rival.chatbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class GeminiAiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiAiService.class);
    private final RestClient restClient;
    private final String apiKey;

    public GeminiAiService(@Value("${spring.ai.openai.api-key:}") String apiKey) {
        this.apiKey = apiKey;
        this.restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public String generateResponse(String userPrompt) {
        if (userPrompt == null || userPrompt.isBlank()) {
            return "Como posso te ajudar hoje?";
        }

        try {
            Map<String, Object> requestBody = Map.of(
                    "system_instruction", Map.of(
                            "parts", List.of(Map.of("text", "Você é o assistente virtual inteligente da Rival Chat. Responda de forma clara e objetiva."))
                    ),
                    "contents", List.of(
                            Map.of(
                                    "role", "user",
                                    "parts", List.of(Map.of("text", userPrompt))
                            )
                    )
            );

            // Atualizado para o modelo suportado na v1beta: gemini-2.5-flash
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
                        return (String) firstPart.get("text");
                    }
                }
            }
            return "Desculpe, não consegui processar a resposta no momento.";
        } catch (Exception e) {
            log.error("Erro ao chamar API nativa do Gemini: ", e);
            return "Ocorreu um erro ao comunicar com a inteligência artificial.";
        }
    }
}