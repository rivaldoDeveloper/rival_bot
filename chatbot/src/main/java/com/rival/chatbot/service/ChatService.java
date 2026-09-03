package com.rival.chatbot.service;

import com.rival.chatbot.dto.ChatRequestDTO;
import com.rival.chatbot.dto.ChatResponseDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface ChatService {
    ChatResponseDTO processMessage(ChatRequestDTO chatRequestDTO);
    ChatResponseDTO processImageFileMessage(UUID sessionId, UUID tenantId, String message, MultipartFile imageFile);
}
