package com.rival.chatbot.service.whatsapp;

import java.util.Map;

public interface WhatsAppWebhookService {
    void processWebhookPayload(String instanceName, Map<String, Object> payload);
}