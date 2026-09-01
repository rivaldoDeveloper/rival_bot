package com.rival.chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ChatRequestDTO(
        @NotNull(message = "O ID da sessão é obrigatório.")
        UUID sessionId,

        @NotNull(message = "O ID do tenant é obrigatório.")
        UUID tenantId,

        @NotBlank(message = "A mensagem não pode ser vazia.")
        String message
) {}
