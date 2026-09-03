package com.rival.chatbot.dto;

import java.time.LocalDateTime;

public record ChatResponseDTO(
        String response,
        String imageUrl,
        String senderType,
        boolean requiresHumanHandoff,
        LocalDateTime timestamp
) {
    // Construtor auxiliar sem imageUrl para manter compatibilidade com respostas apenas de texto
    public ChatResponseDTO(String response, String senderType, boolean requiresHumanHandoff, LocalDateTime timestamp) {
        this(response, null, senderType, requiresHumanHandoff, timestamp);
    }
}