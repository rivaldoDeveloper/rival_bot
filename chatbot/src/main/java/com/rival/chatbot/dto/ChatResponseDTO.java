package com.rival.chatbot.dto;

import java.time.LocalDateTime;

public record ChatResponseDTO(
        String response,
        String senderType,
        boolean requiresHumanHandoff,
        LocalDateTime timestamp
) {}
