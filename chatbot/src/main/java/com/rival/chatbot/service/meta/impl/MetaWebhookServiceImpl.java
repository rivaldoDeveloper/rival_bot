package com.rival.chatbot.service.meta.impl;

import com.rival.chatbot.domain.meta.MetaAccountEntity;
import com.rival.chatbot.dto.ChatRequestDTO;
import com.rival.chatbot.dto.ChatResponseDTO;
import com.rival.chatbot.dto.meta.MetaWebhookDTO;
import com.rival.chatbot.repository.CustomerDataRepository;
import com.rival.chatbot.repository.meta.MetaAccountRepository;
import com.rival.chatbot.service.ChatService;
import com.rival.chatbot.service.meta.MetaSenderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class MetaWebhookServiceImpl {

    private static final Logger log = LoggerFactory.getLogger(MetaWebhookServiceImpl.class);

    private final ChatService chatService;
    private final MetaSenderService metaSenderService;
    private final MetaAccountRepository metaAccountRepository;
    private final CustomerDataRepository customerDataRepository;

    public MetaWebhookServiceImpl(ChatService chatService,
                                  MetaSenderService metaSenderService,
                                  MetaAccountRepository metaAccountRepository,
                                  CustomerDataRepository customerDataRepository) {
        this.chatService = chatService;
        this.metaSenderService = metaSenderService;
        this.metaAccountRepository = metaAccountRepository;
        this.customerDataRepository = customerDataRepository;
    }

    public void processPayload(MetaWebhookDTO payload) {
        try {
            if (payload.entry() == null || payload.entry().isEmpty()) return;

            for (MetaWebhookDTO.Entry entry : payload.entry()) {
                String pageId = entry.id();

                MetaAccountEntity account = metaAccountRepository.findByPageId(pageId).orElse(null);
                if (account == null) {
                    log.warn("Mensagem recebida para PageID desconhecido: {}", pageId);
                    continue;
                }

                if (entry.messaging() == null) continue;

                for (MetaWebhookDTO.Messaging messaging : entry.messaging()) {
                    if (messaging.message() == null || messaging.sender() == null) continue;

                    String senderId = messaging.sender().id();
                    String userText = messaging.message().text();
                    String mediaUrl = null;

                    // Extrai anexos caso o utilizador envie imagem/áudio no FB/Insta
                    if (messaging.message().attachments() != null && !messaging.message().attachments().isEmpty()) {
                        mediaUrl = messaging.message().attachments().get(0).payload().url();
                    }

                    String finalContent = userText != null ? userText : "";
                    if (mediaUrl != null) {
                        finalContent = finalContent.isEmpty() ? mediaUrl : finalContent + "\n" + mediaUrl;
                    }

                    if (finalContent.isEmpty()) continue;

                    UUID sessionId = UUID.nameUUIDFromBytes(senderId.getBytes());
                    String channelName = "page".equalsIgnoreCase(payload.object()) ? "MESSENGER" : "INSTAGRAM";

                    ChatRequestDTO chatRequest = new ChatRequestDTO(sessionId, account.getTenantId(), finalContent, channelName, senderId);
                    ChatResponseDTO response = chatService.processFlowMessage(chatRequest);

                    if (response != null) {
                        String respText = response.response();

                        if (respText != null && (respText.contains("|||") || respText.startsWith("TEXT:") || respText.startsWith("AUDIO:") || respText.startsWith("VIDEO:"))) {
                            String[] parts = respText.split("\\|\\|\\|");
                            for (String part : parts) {
                                if (part.startsWith("AUDIO:")) {
                                    metaSenderService.sendMediaMessage(account.getPageAccessToken(), senderId, "audio", part.substring(6));
                                } else if (part.startsWith("VIDEO:")) {
                                    metaSenderService.sendMediaMessage(account.getPageAccessToken(), senderId, "video", part.substring(6));
                                } else if (part.startsWith("TEXT:")) {
                                    metaSenderService.sendMessage(account.getPageAccessToken(), senderId, part.substring(5));
                                }
                            }
                        } else {
                            if (response.audioUrl() != null && !response.audioUrl().isBlank()) {
                                metaSenderService.sendMediaMessage(account.getPageAccessToken(), senderId, "audio", response.audioUrl());
                            }
                            if (response.imageUrl() != null && !response.imageUrl().isBlank()) {
                                metaSenderService.sendMediaMessage(account.getPageAccessToken(), senderId, "video", response.imageUrl());
                            }
                            if (respText != null && !respText.isBlank()) {
                                metaSenderService.sendMessage(account.getPageAccessToken(), senderId, respText);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Erro ao processar Webhook da Meta", e);
        }
    }
}