package com.rival.chatbot.controller.whatsapp;

import com.rival.chatbot.domain.whatsapp.WhatsAppAccountEntity;
import com.rival.chatbot.dto.ChatRequestDTO;
import com.rival.chatbot.dto.ChatResponseDTO;
import com.rival.chatbot.dto.whatsapp.WhatsAppWebhookDTO;
import com.rival.chatbot.repository.whatsapp.WhatsAppAccountRepository;
import com.rival.chatbot.service.ChatService;
import com.rival.chatbot.service.whatsapp.WhatsAppSenderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/whatsapp")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    @Value("${whatsapp.api.verify-token:meu_token_secreto_123}")
    private String verifyToken;

    private final ChatService chatService;
    private final WhatsAppSenderService whatsAppSenderService;
    private final WhatsAppAccountRepository whatsAppAccountRepository;

    public WhatsAppWebhookController(ChatService chatService,
                                     WhatsAppSenderService whatsAppSenderService,
                                     WhatsAppAccountRepository whatsAppAccountRepository) {
        this.chatService = chatService;
        this.whatsAppSenderService = whatsAppSenderService;
        this.whatsAppAccountRepository = whatsAppAccountRepository;
    }

    /**
     * Validação do Handshake do Webhook exigido pela Meta
     */
    @GetMapping("/webhook")
    public ResponseEntity<String> verifyWebhook(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge) {

        if ("subscribe".equals(mode) && verifyToken.equals(token)) {
            log.info("Handshake do Webhook do WhatsApp verificado com sucesso!");
            return ResponseEntity.ok(challenge);
        }
        log.warn("Falha na verificação do Webhook. Token incorreto ou modo inválido.");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    /**
     * Recebimento de mensagens em tempo real enviado pela Meta
     */
    @PostMapping("/webhook")
    public ResponseEntity<Void> receiveMessage(@RequestBody WhatsAppWebhookDTO payload) {
        try {
            if (payload.entry() != null && !payload.entry().isEmpty()) {
                var change = payload.entry().get(0).changes().get(0);
                var value = change.value();

                // 1. Extrai o Phone Number ID do destinatário (Qual conta de WhatsApp Business recebeu)
                String recipientPhoneNumberId = value.metadata().phoneNumberId();

                if (value.messages() != null && !value.messages().isEmpty()) {
                    var message = value.messages().get(0);

                    // Processa apenas mensagens do tipo texto
                    if ("text".equals(message.type())) {
                        String userPhoneNumber = message.from();
                        String userText = message.text().body();

                        log.info("Mensagem recebida do numero {} para o Phone Number ID Meta {}", userPhoneNumber, recipientPhoneNumberId);

                        // 2. Busca dinâmica das credenciais do Tenant no PostgreSQL pelo Phone Number ID
                        WhatsAppAccountEntity account = whatsAppAccountRepository.findByPhoneNumberId(recipientPhoneNumberId)
                                .orElseThrow(() -> new RuntimeException("Conta de WhatsApp não cadastrada no sistema para o Phone Number ID: " + recipientPhoneNumberId));

                        // 3. Cria ID de Sessão isolado por telefone de origem e Tenant ID
                        UUID sessionId = UUID.nameUUIDFromBytes(userPhoneNumber.getBytes());
                        UUID tenantId = account.getTenantId();

                        // 4. Processamento NLP e regras de negócio
                        ChatRequestDTO chatRequest = new ChatRequestDTO(sessionId, tenantId, userText);
                        ChatResponseDTO response = chatService.processMessage(chatRequest);

                        // 5. Disparo da resposta via Graph API com o Token específico do Tenant encontrado no banco
                        whatsAppSenderService.sendMessage(
                                account.getPhoneNumberId(),
                                account.getApiToken(),
                                userPhoneNumber,
                                response.response()
                        );
                    }
                }
            }
        } catch (Exception e) {
            log.error("Erro ao processar fluxo dinâmico do Webhook do WhatsApp", e);
        }

        // Sempre retorna HTTP 200 OK para a Meta não suspender o Webhook por falha de entrega
        return ResponseEntity.ok().build();
    }
}