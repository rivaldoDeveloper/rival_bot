package com.rival.user_service_api.dto;

import java.util.UUID;

public record UserResponseDTO(UUID id, String name, String email, boolean active) {}