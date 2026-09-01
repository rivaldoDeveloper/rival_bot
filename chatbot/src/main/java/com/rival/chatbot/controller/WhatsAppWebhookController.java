package com.rival.chatbot.controller;

import com.rival.chatbot.domain.WhatsAppAccountEntity;
import com.rival.chatbot.dto.ChatRequestDTO;
import com.rival.chatbot.dto.ChatResponseDTO;
import com.rival.chatbot.dto.WhatsAppWebhookDTO;
import com.rival.chatbot.repository.WhatsAppAccountRepository;
import com.rival.chatbot.service.ChatService;
import com.rival.chatbot.service.WhatsAppSenderService;
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

    @GetMapping("/webhook")
    public ResponseEntity<String> verifyWebhook(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge) {

        if ("subscribe".equals(mode) && verifyToken.equals(token)) {
            log.info("Handshake do Webhook do WhatsApp verificado com sucesso!");
            return ResponseEntity.ok(challenge);
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> receiveMessage(@RequestBody WhatsAppWebhookDTO payload) {
        try {
            if (payload.entry() != null && !payload.entry().isEmpty()) {
                var change = payload.entry().get(0).changes().get(0);
                var value = change.value();

                // 1. Extração Dinâmica do ID do Número Receptor (Quem recebeu a mensagem no WhatsApp Business)
                String recipientPhoneNumberId = value.metadata().phoneNumberId();

                if (value.messages() != null && !value.messages().isEmpty()) {
                    var message = value.messages().get(0);

                    if ("text".equals(message.type())) {
                        // 2. Extração Dinâmica do Telefone do Cliente (Quem enviou a mensagem)
                        String userPhoneNumber = message.from();
                        String userText = message.text().body();

                        // 3. Busca das Credenciais Dinâmicas do Tenant no PostgreSQL pelo Phone Number ID
                        WhatsAppAccountEntity account = whatsAppAccountRepository.findByPhoneNumberId(recipientPhoneNumberId)
                                .orElseThrow(() -> new RuntimeException("Conta de WhatsApp não cadastrada no sistema: " + recipientPhoneNumberId));

                        // 4. Criação Dinâmica do ID de Sessão isolado por Telefone
                        UUID sessionId = UUID.nameUUIDFromBytes(userPhoneNumber.getBytes());
                        UUID tenantId = account.getTenantId();

                        // 5. Processamento pelo Motor de NLP
                        ChatRequestDTO chatRequest = new ChatRequestDTO(sessionId, tenantId, userText);
                        ChatResponseDTO response = chatService.processMessage(chatRequest);

                        // 6. Envio da Resposta para o WhatsApp com Credenciais Carregadas Dinamicamente
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

        return ResponseEntity.ok().build();
    }
}