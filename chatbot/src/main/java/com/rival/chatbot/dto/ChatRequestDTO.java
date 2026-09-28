package com.rival.chatbot.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ChatRequestDTO(
        @NotNull(message = "O ID da sessão é obrigatório.")
        UUID sessionId,

        @NotNull(message = "O ID do tenant é obrigatório.")
        UUID tenantId,

        String message,
        String base64Image,
        String channel, // NOVO CAMPO
        String externalId // NOVO CAMPO
) {
        /**
         * Construtor secundário de conveniência para requisições de apenas texto.
         * Permite instanciar sem quebrar código antigo, definindo "WHATSAPP" como padrão.
         */
        public ChatRequestDTO(UUID sessionId, UUID tenantId, String message) {
                this(sessionId, tenantId, message, null, "WHATSAPP", null);
        }
        /**
         * Construtor secundário para injetar o canal explicitamente sem base64.
         */
        public ChatRequestDTO(UUID sessionId, UUID tenantId, String message, String channel, String externalId) {
                this(sessionId, tenantId, message, null, channel, externalId);
        }
}