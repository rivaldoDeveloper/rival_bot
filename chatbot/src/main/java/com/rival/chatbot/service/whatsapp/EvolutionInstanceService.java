package com.rival.chatbot.service.whatsapp;

import java.util.UUID;

public interface EvolutionInstanceService {
    String createInstanceAndGetQR(UUID tenantId);
    String getFreshQR(UUID tenantId);
    void activateBot(UUID tenantId);
    void logoutInstance(UUID tenantId);
    String getPairingCode(UUID tenantId, String phoneNumber);
}