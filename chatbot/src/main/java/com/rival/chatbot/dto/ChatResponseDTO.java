package com.rival.chatbot.dto;

import java.time.LocalDateTime;

public record ChatResponseDTO(
        String response,
        String imageUrl,
        String audioUrl,
        String senderType,
        boolean requiresHumanHandoff,
        LocalDateTime timestamp
) {
    public ChatResponseDTO(String response, String senderType, boolean requiresHumanHandoff, LocalDateTime timestamp) {
        this(response, null, null, senderType, requiresHumanHandoff, timestamp);
    }
}