package com.rival.chatbot.controller.meta;

import com.rival.chatbot.dto.meta.MetaWebhookDTO;
import com.rival.chatbot.service.meta.impl.MetaWebhookServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/meta")
public class MetaWebhookController {

    private final MetaWebhookServiceImpl metaWebhookService;
    // Defina este token no seu application.yml (ex: spring.meta.verify-token) ou use um fixo para validar a ligação ao painel da Meta
    private final String GLOBAL_VERIFY_TOKEN = "meu_token_secreto_meta_123";

    public MetaWebhookController(MetaWebhookServiceImpl metaWebhookService) {
        this.metaWebhookService = metaWebhookService;
    }

    // 1. Endpoint GET para validação do Webhook no painel da Meta for Developers
    @GetMapping("/webhook")
    public ResponseEntity<String> verifyWebhook(@RequestParam("hub.mode") String mode,
                                                @RequestParam("hub.verify_token") String token,
                                                @RequestParam("hub.challenge") String challenge) {
        if ("subscribe".equals(mode) && GLOBAL_VERIFY_TOKEN.equals(token)) {
            return ResponseEntity.ok(challenge);
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    // 2. Endpoint POST para receção de mensagens dos clientes
    @PostMapping("/webhook")
    public ResponseEntity<Void> receiveMessage(@RequestBody MetaWebhookDTO payload) {
        metaWebhookService.processPayload(payload);
        return ResponseEntity.ok().build(); // Retorna 200 OK imediato para a Meta não efetuar retries
    }
}