package com.rival.chatbot.controller.meta;

import com.rival.chatbot.dto.meta.MetaWebhookDTO;
import com.rival.chatbot.repository.meta.MetaAccountRepository;
import com.rival.chatbot.service.meta.impl.MetaWebhookServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/meta")
@Tag(name = "Meta Webhook", description = "Endpoints para a integração do Webhook oficial da Meta for Developers")
public class MetaWebhookController {

    private final MetaWebhookServiceImpl metaWebhookService;
    private final MetaAccountRepository metaAccountRepository;

    @Value("${whatsapp.api.verify-token:}")
    private String fallbackVerifyToken;

    public MetaWebhookController(MetaWebhookServiceImpl metaWebhookService,
                                 MetaAccountRepository metaAccountRepository) {
        this.metaWebhookService = metaWebhookService;
        this.metaAccountRepository = metaAccountRepository;
    }

    @Operation(summary = "Validar Webhook (GET)", description = "Endpoint de validação exigido pela Meta para confirmar a URL do Webhook.")
    @GetMapping("/webhook")
    public ResponseEntity<String> verifyWebhook(@RequestParam("hub.mode") String mode,
                                                @RequestParam("hub.verify_token") String token,
                                                @RequestParam("hub.challenge") String challenge) {
        boolean tokenMatchedInDb = metaAccountRepository.findAll().stream()
                .anyMatch(acc -> token != null && token.equals(acc.getVerifyToken()));

        boolean tokenMatchedFallback = fallbackVerifyToken != null
                && !fallbackVerifyToken.isBlank()
                && fallbackVerifyToken.equals(token);

        if ("subscribe".equals(mode) && (tokenMatchedInDb || tokenMatchedFallback)) {
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