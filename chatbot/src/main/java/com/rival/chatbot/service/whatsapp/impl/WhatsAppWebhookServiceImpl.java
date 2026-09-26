package com.rival.chatbot.service.whatsapp.impl;

import com.rival.chatbot.domain.whatsapp.WhatsAppAccountEntity;
import com.rival.chatbot.dto.ChatRequestDTO;
import com.rival.chatbot.dto.ChatResponseDTO;
import com.rival.chatbot.repository.whatsapp.WhatsAppAccountRepository;
import com.rival.chatbot.service.ChatService;
import com.rival.chatbot.service.whatsapp.WhatsAppSenderService;
import com.rival.chatbot.service.whatsapp.WhatsAppWebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class WhatsAppWebhookServiceImpl implements WhatsAppWebhookService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookServiceImpl.class);

    private final ChatService chatService;
    private final WhatsAppSenderService whatsAppSenderService;
    private final WhatsAppAccountRepository whatsAppAccountRepository;

    public WhatsAppWebhookServiceImpl(ChatService chatService,
                                      WhatsAppSenderService whatsAppSenderService,
                                      WhatsAppAccountRepository whatsAppAccountRepository) {
        this.chatService = chatService;
        this.whatsAppSenderService = whatsAppSenderService;
        this.whatsAppAccountRepository = whatsAppAccountRepository;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void processWebhookPayload(String instanceName, Map<String, Object> payload) {
        try {
            if (!"messages.upsert".equals(payload.get("event"))) {
                return;
            }

            Map<String, Object> data = (Map<String, Object>) payload.get("data");
            if (data == null) return;

            Map<String, Object> msgObject = data.containsKey("message") ? (Map<String, Object>) data.get("message") : data;

            String userPhoneNumber = extractPhoneNumber(data, msgObject);
            Boolean fromMe = extractFromMe(data, msgObject);

            if (userPhoneNumber == null || userPhoneNumber.contains("@g.us") || Boolean.TRUE.equals(fromMe)) {
                return;
            }

            String userText = extractUserText(msgObject);

            if (userText == null || userText.isBlank()) {
                return;
            }

            log.info("📩 Mensagem recebida -> De: {}, Texto: '{}'", userPhoneNumber, userText);

            userPhoneNumber = userPhoneNumber.split("@")[0];

            WhatsAppAccountEntity account = whatsAppAccountRepository.findByInstanceName(instanceName)
                    .orElseThrow(() -> new IllegalArgumentException("Instância não encontrada no banco: " + instanceName));

            UUID sessionId = UUID.nameUUIDFromBytes(userPhoneNumber.getBytes());

            ChatRequestDTO chatRequest = new ChatRequestDTO(sessionId, account.getTenantId(), userText);
            ChatResponseDTO response = chatService.processFlowMessage(chatRequest);

            whatsAppSenderService.sendMessage(
                    account.getInstanceName(),
                    account.getEvolutionApiKey(),
                    userPhoneNumber,
                    response.response()
            );

        } catch (Exception e) {
            log.error("Erro interno ao processar Webhook da Evolution API: ", e);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractPhoneNumber(Map<String, Object> data, Map<String, Object> msgObject) {
        if (data.containsKey("key")) {
            return (String) ((Map<String, Object>) data.get("key")).get("remoteJid");
        } else if (msgObject.containsKey("key")) {
            return (String) ((Map<String, Object>) msgObject.get("key")).get("remoteJid");
        }
        return (String) data.get("remoteJid");
    }

    @SuppressWarnings("unchecked")
    private Boolean extractFromMe(Map<String, Object> data, Map<String, Object> msgObject) {
        if (data.containsKey("key")) {
            return (Boolean) ((Map<String, Object>) data.get("key")).get("fromMe");
        } else if (msgObject.containsKey("key")) {
            return (Boolean) ((Map<String, Object>) msgObject.get("key")).get("fromMe");
        }
        return (Boolean) data.get("fromMe");
    }

    @SuppressWarnings("unchecked")
    private String extractUserText(Map<String, Object> msgObject) {
        if (msgObject == null) return null;

        Map<String, Object> messageContent = msgObject;
        if (msgObject.containsKey("message") && msgObject.get("message") instanceof Map) {
            messageContent = (Map<String, Object>) msgObject.get("message");
        }

        // 1. Mensagem de texto simples
        if (messageContent.containsKey("conversation")) {
            return (String) messageContent.get("conversation");
        }

        // 2. Mensagem longa, resposta a outra mensagem, ou WhatsApp Web (Bug corrigido)
        if (messageContent.containsKey("extendedTextMessage")) {
            Object extText = messageContent.get("extendedTextMessage");
            if (extText instanceof Map) {
                return (String) ((Map<String, Object>) extText).get("text");
            }
        }

        // 3. Texto que vem na legenda de uma imagem
        if (messageContent.containsKey("imageMessage")) {
            Object imgMsg = messageContent.get("imageMessage");
            if (imgMsg instanceof Map && ((Map<?, ?>) imgMsg).containsKey("caption")) {
                return (String) ((Map<String, Object>) imgMsg).get("caption");
            }
        }

        return null;
    }
}