package com.rival.chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record NlpIntentDTO(
        @NotBlank(message = "O nome da intenção é obrigatório.")
        String name,

        @NotBlank(message = "O idioma é obrigatório.")
        String language,

        @NotEmpty(message = "A lista de palavras-chave não pode ser vazia.")
        List<String> keywords,

        @NotEmpty(message = "A lista de respostas não pode ser vazia.")
        List<String> responses
) {}