package com.rival.chatbot.service;

import com.rival.chatbot.dto.ChatRequestDTO;
import com.rival.chatbot.dto.ChatResponseDTO;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.UUID;

public interface ChatService {

    ChatResponseDTO processMessage(ChatRequestDTO chatRequestDTO);

    ChatResponseDTO processImageFileMessage(UUID sessionId, UUID tenantId, String message, MultipartFile imageFile);

    /**
     * Método padrão para processamento de áudio.
     * Implementações legadas que não sobrescreverem este método
     * utilizarão este fallback automaticamente sem quebrar a compilação.
     */
    default ChatResponseDTO processAudioFileMessage(UUID sessionId, UUID tenantId, File audioFile) {
        return processMessage(new ChatRequestDTO(sessionId, tenantId, "Áudio enviado para processamento."));
    }
}