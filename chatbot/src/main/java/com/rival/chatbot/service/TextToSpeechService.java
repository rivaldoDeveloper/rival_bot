package com.rival.chatbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class TextToSpeechService {

    private static final Logger log = LoggerFactory.getLogger(TextToSpeechService.class);
    private final RestClient restClient = RestClient.builder().build();

    public File generateAudioFromText(String text) {
        if (text == null || text.isBlank()) return null;

        try {
            log.info("Gerando voz para o bot dizer: '{}'", text);
            String cleanedText = text.replaceAll("[^a-zA-Z0-9 áàâãéèêíïóôõöúçÑñÁÀÂÃÉÈÊÍÏÓÔÕÖÚÇ.,?!]", "");
            String encodedText = URLEncoder.encode(cleanedText, StandardCharsets.UTF_8);

            // API Pública e gratuita
            String url = "https://translate.google.com/translate_tts?ie=UTF-8&tl=pt-BR&client=tw-ob&q=" + encodedText;

            byte[] audioBytes = restClient.get()
                    .uri(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .retrieve()
                    .body(byte[].class);

            if (audioBytes == null || audioBytes.length == 0) {
                return null;
            }

            File outputDir = new File("uploads");
            if (!outputDir.exists()) outputDir.mkdirs();

            File audioFile = new File(outputDir, "bot_voice_" + UUID.randomUUID() + ".mp3");
            try (FileOutputStream fos = new FileOutputStream(audioFile)) {
                fos.write(audioBytes);
            }

            return audioFile;

        } catch (Exception e) {
            log.error("Falha ao sintetizar áudio de voz: ", e);
            return null;
        }
    }
}