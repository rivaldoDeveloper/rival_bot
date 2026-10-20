package com.rival.user_service_api.dto.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateCheckoutRequestDTO(
        @NotNull(message = "O ID do Tenant é obrigatório")
        UUID tenantId,

        @NotBlank(message = "O ID do Preço da Stripe é obrigatório")
        String priceId // ex: "price_1N..." do painel da Stripe
) {}