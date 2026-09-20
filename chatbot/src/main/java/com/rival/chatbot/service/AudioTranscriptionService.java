package com.rival.chatbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class AudioTranscriptionService {

    private static final Logger log = LoggerFactory.getLogger(AudioTranscriptionService.class);

    public String transcribeAudioFile(File audioFile) {
        if (audioFile != null && audioFile.exists()) {
            log.info("Áudio recebido e preservado no disco: {}. Motor de transcrição (STT) desativado.", audioFile.getAbsolutePath());
        }

        // Retornar esta string exata faz o ChatServiceImpl devolver a mensagem de erro padronizada
        return "áudio inaudível";
    }
}