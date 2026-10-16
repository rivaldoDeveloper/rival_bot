package com.rival.user_service_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePasswordRequestDTO(
        @NotBlank(message = "A palavra-passe atual é obrigatória")
        String currentPassword,

        @NotBlank(message = "A nova palavra-passe é obrigatória")
        @Size(min = 6, message = "A nova palavra-passe deve ter pelo menos 6 caracteres")
        String newPassword
) {}