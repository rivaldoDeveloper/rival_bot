package com.rival.helpdesk_bff.dto;

public record DashboardResponseDTO(
        Object userInfo,
        Object activeSessions
) {}