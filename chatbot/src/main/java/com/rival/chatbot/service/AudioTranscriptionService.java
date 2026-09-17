

//package com.rival.chatbot.service;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.stereotype.Service;
//
//import java.io.File;
//
//@Service
//public class AudioTranscriptionService {
//
//    private static final Logger log = LoggerFactory.getLogger(AudioTranscriptionService.class);
//
//    public String transcribeAudioFile(File audioFile) {
//        log.warn("Tentativa de processar áudio recebida, mas o motor offline está desativado devido a políticas do AppLocker/Windows.");
//
//        // Retorna uma mensagem amigável para o usuário indicando a limitação atual
//        return "Desculpe, o processamento de áudio está temporariamente desativado no ambiente atual. Por favor, envie sua dúvida por texto.";
//    }
//}


package com.rival.chatbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.vosk.Model;
import org.vosk.Recognizer;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

@Service
public class AudioTranscriptionService {

    private static final Logger log = LoggerFactory.getLogger(AudioTranscriptionService.class);

    // Força o diretório temporário para evitar bloqueio do AppLocker
    static {
        System.setProperty("jna.tmpdir", System.getProperty("java.io.tmpdir"));
    }

    private static final String VOSK_PATH_ROOT = "./models/vosk-pt";
    private static final String VOSK_PATH_SRC = "./src/main/java/com/rival/chatbot/models/vosk-pt";

    private Model voskModel;

    @PostConstruct
    public void init() {
        String resolvedPath = null;

        if (Files.exists(Paths.get(VOSK_PATH_ROOT))) {
            resolvedPath = VOSK_PATH_ROOT;
        } else if (Files.exists(Paths.get(VOSK_PATH_SRC))) {
            resolvedPath = VOSK_PATH_SRC;
        } else {
            log.error("Vosk não encontrou o modelo de linguagem.");
            return;
        }

        try {
            this.voskModel = new Model(resolvedPath);
            log.info("Motor de reconhecimento de voz local iniciado!");
        } catch (Exception e) {
            log.error("Falha ao inicializar o motor Vosk", e);
        }
    }

    public String transcribeAudioFile(File audioFile) {
        if (audioFile == null || !audioFile.exists() || this.voskModel == null) {
            return "";
        }

        // Converte o OGG (WhatsApp/Telegram) para WAV 16khz Mono (Formato obrigatório do Vosk)
        File wavAudioFile = convertToWav(audioFile);

        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(wavAudioFile));
             Recognizer recognizer = new Recognizer(voskModel, 16000)) {

            byte[] buffer = new byte[4096];
            int bytesRead;

            while ((bytesRead = bis.read(buffer)) != -1) {
                recognizer.acceptWaveForm(buffer, bytesRead);
            }

            String jsonResult = recognizer.getFinalResult();
            String transcribedText = extractTextFromJson(jsonResult);

            // Apaga arquivo WAV temporário
            if (!wavAudioFile.getAbsolutePath().equals(audioFile.getAbsolutePath())) {
                wavAudioFile.delete();
            }

            // Limpa formatações e retorna
            transcribedText = transcribedText.trim();
            if (transcribedText.isEmpty()) {
                return "áudio inaudível";
            }
            return transcribedText;

        } catch (Exception e) {
            log.error("Erro durante a transcrição do áudio local", e);
            return "erro ao processar a fala";
        }
    }

    private File convertToWav(File sourceFile) {
        if (sourceFile.getName().toLowerCase().endsWith(".wav")) {
            return sourceFile;
        }

        try {
            File destWav = new File(sourceFile.getAbsolutePath() + "_converted.wav");

            // O segredo está aqui: Força a frequência de amostragem correta para o Vosk e corta silêncio
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-y", "-i", sourceFile.getAbsolutePath(),
                    "-ar", "16000", "-ac", "1", "-c:a", "pcm_s16le",
                    destWav.getAbsolutePath()
            );

            Process p = pb.start();
            p.waitFor();

            if (destWav.exists() && destWav.length() > 0) {
                return destWav;
            }
        } catch (Exception e) {
            log.error("Falha na conversão via FFmpeg. Verifique se ele está no PATH.", e);
        }
        return sourceFile;
    }

    private String extractTextFromJson(String json) {
        try {
            if (json.contains("\"text\" : \"")) {
                int start = json.indexOf("\"text\" : \"") + 10;
                int end = json.indexOf("\"", start);
                return json.substring(start, end);
            }
        } catch (Exception e) {
            log.trace("Erro ao extrair texto", e);
        }
        return "";
    }

    @PreDestroy
    public void closeModel() {
        if (this.voskModel != null) {
            this.voskModel.close();
        }
    }
}