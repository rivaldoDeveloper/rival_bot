package com.rival.user_service_api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UpdateProfileRequestDTO(
        @NotBlank(message = "O nome não pode estar em branco")
        String name,

        @NotBlank(message = "O e-mail não pode estar em branco")
        @Email(message = "Formato de e-mail inválido")
        String email
) {}