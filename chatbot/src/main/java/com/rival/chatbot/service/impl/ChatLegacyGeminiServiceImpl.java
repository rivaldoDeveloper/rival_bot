package com.rival.chatbot.service.impl;

import com.rival.chatbot.domain.ChatMessageEntity;
import com.rival.chatbot.dto.ChatRequestDTO;
import com.rival.chatbot.dto.ChatResponseDTO;
import com.rival.chatbot.mapper.ChatMapper;
import com.rival.chatbot.repository.ChatMessageRepository;
import com.rival.chatbot.service.ChatService;
import com.rival.chatbot.service.DataExtractorService;
import com.rival.chatbot.service.GeminiAiService;
import com.rival.chatbot.service.OcrService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.UUID;

//@Service
@SuppressWarnings({"all", "java:S1186", "java:S1192"})
public class ChatLegacyGeminiServiceImpl implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatLegacyGeminiServiceImpl.class);
    private final ChatMessageRepository repository;
    private final ChatMapper chatMapper;
    private final GeminiAiService geminiAiService;
    private final DataExtractorService dataExtractorService;
    private final OcrService ocrService;

    public ChatLegacyGeminiServiceImpl(ChatMessageRepository repository,
                                       ChatMapper chatMapper,
                                       GeminiAiService geminiAiService,
                                       DataExtractorService dataExtractorService,
                                       OcrService ocrService) {
        this.repository = repository;
        this.chatMapper = chatMapper;
        this.geminiAiService = geminiAiService;
        this.dataExtractorService = dataExtractorService;
        this.ocrService = ocrService;
    }

    @Override
    @Transactional
    public ChatResponseDTO processMessage(ChatRequestDTO request) {
        String userTextContent = resolveInputContent(request);
        return handleChatFlow(request.sessionId(), request.tenantId(), userTextContent, request.channel(), request.externalId());
    }

    @Override
    @Transactional
    public ChatResponseDTO processImageFileMessage(UUID sessionId, UUID tenantId, String message, MultipartFile imageFile) {
        return handleChatFlow(sessionId, tenantId, "Imagem recebida", "WHATSAPP", null);
    }

    @Override
    public ChatResponseDTO processFlowMessage(ChatRequestDTO chatRequestDTO) {
        return null;
    }

    private ChatResponseDTO handleChatFlow(UUID sessionId, UUID tenantId, String userTextContent, String channel, String externalId) {
        ChatMessageEntity userEntity = new ChatMessageEntity();
        userEntity.setSessionId(sessionId);
        userEntity.setTenantId(tenantId);
        userEntity.setContent(userTextContent);
        userEntity.setSenderType("USER");
        repository.save(userEntity);

        // ✅ Correção: Passando os 5 parâmetros exigidos
        dataExtractorService.extractAndSave(sessionId, tenantId, userTextContent, channel, externalId);

        String userMsgLower = userTextContent.toLowerCase();
        if (userMsgLower.contains("atendente") || userMsgLower.contains("humano") || userMsgLower.contains("suporte")) {
            String handoffResponse = "Entendido! Estou transferindo o seu atendimento para um operador humano.";
            saveBotResponse(sessionId, tenantId, handoffResponse);
            return new ChatResponseDTO(handoffResponse, "BOT", true, LocalDateTime.now());
        }

        String aiResponse = geminiAiService.generateResponse(userTextContent);
        saveBotResponse(sessionId, tenantId, aiResponse);
        return new ChatResponseDTO(aiResponse, "BOT", false, LocalDateTime.now());
    }

    private String resolveInputContent(ChatRequestDTO request) {
        return request.message() != null ? request.message() : "";
    }

    private void saveBotResponse(UUID sessionId, UUID tenantId, String responseContent) {
        ChatMessageEntity botEntity = new ChatMessageEntity();
        botEntity.setSessionId(sessionId);
        botEntity.setTenantId(tenantId);
        botEntity.setContent(responseContent);
        botEntity.setSenderType("BOT");
        repository.save(botEntity);
    }

    @Override
    @Transactional
    public ChatResponseDTO processAgentMessage(ChatRequestDTO request) {
        return new ChatResponseDTO(request.message(), null, null, "AGENT", false, LocalDateTime.now());
    }

    @Override
    @Transactional
    public ChatResponseDTO processAgentMediaMessage(UUID sessionId, UUID tenantId, String message, MultipartFile file) {
        return new ChatResponseDTO(message, null, null, "AGENT", false, LocalDateTime.now());
    }

    @Override
    @Transactional
    public void deleteMessage(UUID id) {
        repository.deleteById(id);
    }
}