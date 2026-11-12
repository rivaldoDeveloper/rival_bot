package com.rival.chatbot.service.whatsapp;

import java.util.Map;

@SuppressWarnings("rawtypes")
public interface WhatsAppWebhookService {
    void processWebhookPayload(String instanceName, Map payload);
}