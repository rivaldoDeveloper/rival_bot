package com.rival.chatbot.controller.whatsapp;

import com.rival.chatbot.service.whatsapp.WhatsAppWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/whatsapp")
public class WhatsAppWebhookController {

    private final WhatsAppWebhookService webhookService;

    public WhatsAppWebhookController(WhatsAppWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/webhook/{instanceName}")
    public ResponseEntity<Void> receiveMessage(@PathVariable String instanceName, @RequestBody Map<String, Object> payload) {

        // Passa a bola para a camada de serviço.
        webhookService.processWebhookPayload(instanceName, payload);

        // Retorna sempre 200 OK imediato para a Evolution API não efetuar retry de mensagens.
        return ResponseEntity.ok().build();
    }
}