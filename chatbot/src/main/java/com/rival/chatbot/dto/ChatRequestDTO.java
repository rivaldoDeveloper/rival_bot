package com.rival.chatbot.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ChatRequestDTO(
        @NotNull(message = "O ID da sessão é obrigatório.")
        UUID sessionId,
        @NotNull(message = "O ID do tenant é obrigatório.")
        UUID tenantId,
        String message,
        String base64Image,
        String channel,
        String externalId
) {

        @JsonCreator
        public ChatRequestDTO(
                @JsonProperty("sessionId") UUID sessionId,
                @JsonProperty("tenantId") UUID tenantId,
                @JsonProperty("message") String message,
                @JsonProperty("base64Image") String base64Image,
                @JsonProperty("channel") String channel,
                @JsonProperty("externalId") String externalId) {

                this.sessionId = sessionId;
                this.tenantId = tenantId;
                this.message = message;
                this.base64Image = base64Image;
                // Se o canal vier vazio, assume WHATSAPP
                this.channel = (channel != null && !channel.isBlank()) ? channel : "WHATSAPP";
                this.externalId = externalId;
        }

        // Construtor legado de 3 argumentos
        public ChatRequestDTO(UUID sessionId, UUID tenantId, String message) {
                this(sessionId, tenantId, message, null, "WHATSAPP", null);
        }

        // Construtor legado de 4 argumentos
        public ChatRequestDTO(UUID sessionId, UUID tenantId, String message, String channel) {
                this(sessionId, tenantId, message, null, channel, null);
        }

        // Construtor legado de 5 argumentos
        public ChatRequestDTO(UUID sessionId, UUID tenantId, String message, String channel, String externalId) {
                this(sessionId, tenantId, message, null, channel, externalId);
        }
}