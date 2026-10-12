package com.rival.chatbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.util.Map;

@Service
public class AudioTranscriptionService {

    private static final Logger log = LoggerFactory.getLogger(AudioTranscriptionService.class);
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${ai.openai.api-key:}")
    private String openAiApiKey;

    public String transcribeAudioFile(File audioFile) {
        if (audioFile == null || !audioFile.exists()) {
            return "Áudio não encontrado.";
        }

        if (openAiApiKey == null || openAiApiKey.isBlank() || openAiApiKey.equals("chave_ficticia_teste")) {
            log.warn("Chave da OpenAI não configurada. Transcrição ignorada.");
            return "Áudio recebido, mas o motor de transcrição está desligado.";
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setBearerAuth(openAiApiKey);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new FileSystemResource(audioFile));
            body.add("model", "whisper-1");
            body.add("language", "pt"); // Força o reconhecimento em português

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "https://api.openai.com/v1/audio/transcriptions",
                    requestEntity,
                    Map.class
            );

            if (response.getBody() != null && response.getBody().containsKey("text")) {
                String transcribedText = (String) response.getBody().get("text");
                log.info("Áudio transcrito com sucesso: {}", transcribedText);
                return transcribedText;
            }
        } catch (Exception e) {
            log.error("Erro ao transcrever áudio na OpenAI: ", e);
        }

        return "Não foi possível compreender o áudio neste momento.";
    }
}