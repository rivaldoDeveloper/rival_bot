package com.rival.chatbot.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ChatRequestDTO(
        @NotNull(message = "O ID da sessão é obrigatório.")
        UUID sessionId,

        @NotNull(message = "O ID do tenant é obrigatório.")
        UUID tenantId,

        String message,

        String base64Image
) {
        /**
         * Construtor secundário de conveniência para requisições de apenas texto.
         * Permite instanciar new ChatRequestDTO(sessionId, tenantId, userText) sem quebrar código antigo.
         */
        public ChatRequestDTO(UUID sessionId, UUID tenantId, String message) {
                this(sessionId, tenantId, message, null);
        }
}