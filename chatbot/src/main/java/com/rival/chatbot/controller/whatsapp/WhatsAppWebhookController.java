package com.rival.chatbot.controller.whatsapp;

import com.rival.chatbot.service.whatsapp.WhatsAppWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/whatsapp")
@Tag(name = "WhatsApp Webhook", description = "Endpoint de escuta para eventos e mensagens processados pela Evolution API")
public class WhatsAppWebhookController {

    private final WhatsAppWebhookService webhookService;

    public WhatsAppWebhookController(WhatsAppWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @Operation(summary = "Receber Eventos (Webhook Evolution)", description = "Passivo: Acionado automaticamente pela Evolution API quando um cliente manda mensagens no WhatsApp.")
    @PostMapping("/webhook/{instanceName}")
    public ResponseEntity<Void> receiveMessage(@PathVariable String instanceName, @RequestBody Map<String, Object> payload) {
        // Passa a bola para a camada de servico
        webhookService.processWebhookPayload(instanceName, payload);
        // Retorna sempre 200 OK imediato para a Evolution API nao efetuar retry de mensagens.
        return ResponseEntity.ok().build();
    }
}