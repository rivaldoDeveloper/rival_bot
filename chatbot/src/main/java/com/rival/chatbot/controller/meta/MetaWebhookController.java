package com.rival.chatbot.controller.meta;

import com.rival.chatbot.dto.meta.MetaWebhookDTO;
import com.rival.chatbot.service.meta.impl.MetaWebhookServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/meta")
@Tag(name = "Meta Webhook", description = "Endpoints para a integracao do Webhook oficial da Meta for Developers")
public class MetaWebhookController {

    private final MetaWebhookServiceImpl metaWebhookService;

    // Defina este token no seu application.yml (ex: spring.meta.verify-token) ou use um fixo para validar a ligacao ao painel da Meta
    private final String GLOBAL_VERIFY_TOKEN = "meu_token_secreto_meta_123";

    public MetaWebhookController(MetaWebhookServiceImpl metaWebhookService) {
        this.metaWebhookService = metaWebhookService;
    }

    // 1. Endpoint GET para validação do Webhook no painel da Meta for Developers
    @Operation(summary = "Validar Webhook (GET)", description = "Endpoint de validacao exigido pela Meta para confirmar a URL do Webhook.")
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
    @Operation(summary = "Receber Mensagens (POST)", description = "Endpoint passivo que recebe todos os eventos e mensagens enviados pelos clientes no Facebook ou Instagram.")
    @PostMapping("/webhook")
    public ResponseEntity<Void> receiveMessage(@RequestBody MetaWebhookDTO payload) {
        metaWebhookService.processPayload(payload);
        return ResponseEntity.ok().build(); // Retorna 200 OK imediato para a Meta nao efetuar retries
    }
}