//package com.rival.chatbot.service;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.stereotype.Service;
//import org.vosk.Model;
//import org.vosk.Recognizer;
//
//import jakarta.annotation.PostConstruct;
//import jakarta.annotation.PreDestroy;
//import java.io.BufferedInputStream;
//import java.io.File;
//import java.io.FileInputStream;
//import java.nio.file.Files;
//import java.nio.file.Paths;
//
//@Service
//public class AudioTranscriptionService {
//
//    private static final Logger log = LoggerFactory.getLogger(AudioTranscriptionService.class);
//
//    // Força o JNA (Vosk) a usar a pasta nativa do usuário do Windows para fugir do AppLocker
//    static {
//        String userHome = System.getProperty("user.home");
//        System.setProperty("jna.tmpdir", userHome);
//    }
//
//    private static final String VOSK_PATH_ROOT = "./models/vosk-pt";
//    private static final String VOSK_PATH_SRC = "./src/main/java/com/rival/chatbot/models/vosk-pt";
//
//    private Model voskModel;
//
//    @PostConstruct
//    public void init() {
//        String resolvedPath = null;
//
//        if (Files.exists(Paths.get(VOSK_PATH_ROOT))) {
//            resolvedPath = VOSK_PATH_ROOT;
//        } else if (Files.exists(Paths.get(VOSK_PATH_SRC))) {
//            resolvedPath = VOSK_PATH_SRC;
//        } else {
//            log.error("CÉREBRO DE ÁUDIO NÃO ENCONTRADO! O Vosk não achou a pasta nos caminhos previstos.");
//            return;
//        }
//
//        try {
//            log.info("Carregando Rede Neural de Reconhecimento de Voz (VOSK) na memória...");
//            this.voskModel = new Model(resolvedPath);
//            log.info("Inteligência de Áudio Local carregada com SUCESSO!");
//        } catch (Exception e) {
//            log.error("Falha ao inicializar o motor Vosk offline.", e);
//        }
//    }
//
//    public String transcribeAudioFile(File audioFile) {
//        if (audioFile == null || !audioFile.exists() || this.voskModel == null) {
//            return "Áudio não reconhecido ou modelo inativo.";
//        }
//
//        File wavAudioFile = convertOggToWav(audioFile);
//
//        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(wavAudioFile));
//             Recognizer recognizer = new Recognizer(voskModel, 16000)) {
//
//            byte[] buffer = new byte[4096];
//            int bytesRead;
//
//            while ((bytesRead = bis.read(buffer)) != -1) {
//                recognizer.acceptWaveForm(buffer, bytesRead);
//            }
//
//            String jsonResult = recognizer.getFinalResult();
//            String transcribedText = extractTextFromJson(jsonResult);
//
//            if (!wavAudioFile.getName().equals(audioFile.getName())) {
//                wavAudioFile.delete();
//            }
//
//            return transcribedText.isBlank() ? "Áudio inaudível" : transcribedText;
//
//        } catch (Exception e) {
//            log.error("Erro durante a transcrição do áudio local", e);
//            return "Erro ao processar a fala.";
//        }
//    }
//
//    private File convertOggToWav(File sourceFile) {
//        if (sourceFile.getName().toLowerCase().endsWith(".wav")) {
//            return sourceFile;
//        }
//
//        try {
//            File destWav = new File(sourceFile.getAbsolutePath().replace(".ogg", ".wav").replace(".oga", ".wav"));
//
//            ProcessBuilder pb = new ProcessBuilder(
//                    "ffmpeg", "-y", "-i", sourceFile.getAbsolutePath(),
//                    "-ar", "16000", "-ac", "1", "-c:a", "pcm_s16le", destWav.getAbsolutePath()
//            );
//
//            Process p = pb.start();
//            p.waitFor();
//
//            if (destWav.exists() && destWav.length() > 0) {
//                return destWav;
//            }
//        } catch (Exception e) {
//            log.error("Falha ao acionar o FFmpeg. Verifique se ele está no PATH do Windows.", e);
//        }
//        return sourceFile;
//    }
//
//    private String extractTextFromJson(String json) {
//        try {
//            if (json.contains("\"text\" : \"")) {
//                int start = json.indexOf("\"text\" : \"") + 10;
//                int end = json.indexOf("\"", start);
//                return json.substring(start, end);
//            }
//        } catch (Exception e) {
//            log.trace("Erro ao extrair texto do JSON", e);
//        }
//        return "";
//    }
//
//    @PreDestroy
//    public void closeModel() {
//        if (this.voskModel != null) {
//            this.voskModel.close();
//        }
//    }
//}

package com.rival.chatbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class AudioTranscriptionService {

    private static final Logger log = LoggerFactory.getLogger(AudioTranscriptionService.class);

    public String transcribeAudioFile(File audioFile) {
        log.warn("Tentativa de processar áudio recebida, mas o motor offline está desativado devido a políticas do AppLocker/Windows.");

        // Retorna uma mensagem amigável para o usuário indicando a limitação atual
        return "Desculpe, o processamento de áudio está temporariamente desativado no ambiente atual. Por favor, envie sua dúvida por texto.";
    }
}