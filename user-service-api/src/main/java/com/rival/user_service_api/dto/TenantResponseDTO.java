package com.rival.user_service_api.dto;

import com.rival.user_service_api.enums.SubscriptionStatus;
import java.time.LocalDate;
import java.util.UUID;

public record TenantResponseDTO(
        UUID id,
        String name,
        String document,
        boolean active,
        SubscriptionStatus subscriptionStatus,
        LocalDate dueDate
) {}