package com.rival.chatbot.service.impl;

import com.rival.chatbot.domain.ChatMessageEntity;
import com.rival.chatbot.dto.ChatRequestDTO;
import com.rival.chatbot.dto.ChatResponseDTO;
import com.rival.chatbot.mapper.ChatMapper;
import com.rival.chatbot.repository.ChatMessageRepository;
import com.rival.chatbot.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.time.LocalDateTime;
import java.util.UUID;

//@Service
@SuppressWarnings({"all", "java:S1186", "java:S1192"})
public class ChatLegacyAudioServiceImpl implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatLegacyAudioServiceImpl.class);
    private final ChatMessageRepository repository;
    private final ChatMapper chatMapper;
    private final LocalVectorNlpService localVectorNlpService;
    private final DataExtractorService dataExtractorService;
    private final OcrService ocrService;
    private final GeminiAiService geminiAiService;
    private final AudioTranscriptionService audioTranscriptionService;

    public ChatLegacyAudioServiceImpl(ChatMessageRepository repository,
                                      ChatMapper chatMapper,
                                      LocalVectorNlpService localVectorNlpService,
                                      DataExtractorService dataExtractorService,
                                      OcrService ocrService,
                                      GeminiAiService geminiAiService,
                                      AudioTranscriptionService audioTranscriptionService) {
        this.repository = repository;
        this.chatMapper = chatMapper;
        this.localVectorNlpService = localVectorNlpService;
        this.dataExtractorService = dataExtractorService;
        this.ocrService = ocrService;
        this.geminiAiService = geminiAiService;
        this.audioTranscriptionService = audioTranscriptionService;
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
        return handleChatFlow(sessionId, tenantId, "Imagem enviada", "WHATSAPP", null);
    }

    @Override
    public ChatResponseDTO processFlowMessage(ChatRequestDTO chatRequestDTO) {
        return null;
    }

    @Override
    @Transactional
    public ChatResponseDTO processAudioFileMessage(UUID sessionId, UUID tenantId, File audioFile) {
        return handleChatFlow(sessionId, tenantId, "Áudio recebido", "WHATSAPP", null);
    }

    private ChatResponseDTO handleChatFlow(UUID sessionId, UUID tenantId, String userTextContent, String channel, String externalId) {
        saveUserMessage(sessionId, tenantId, userTextContent);

        // ✅ Correção: Passando os 5 parâmetros exigidos
        dataExtractorService.extractAndSave(sessionId, tenantId, userTextContent, channel, externalId);

        if (isHumanHandoff(userTextContent)) {
            return triggerHandoff(sessionId, tenantId);
        }

        String localContext = localVectorNlpService.processAndMatch(sessionId, userTextContent);
        String finalPrompt = userTextContent;
        if (localContext != null && !localContext.contains("Desculpe, não consegui entender")) {
            finalPrompt = "Com base nas seguintes informações da empresa: " + localContext + "\n\nResponda à solicitação do cliente: " + userTextContent;
        }

        String finalResponse = geminiAiService.generateResponse(finalPrompt);
        saveBotResponse(sessionId, tenantId, finalResponse);
        return new ChatResponseDTO(finalResponse, "BOT", false, LocalDateTime.now());
    }

    private void saveUserMessage(UUID sessionId, UUID tenantId, String userTextContent) {
        ChatMessageEntity userEntity = new ChatMessageEntity();
        userEntity.setSessionId(sessionId);
        userEntity.setTenantId(tenantId);
        userEntity.setContent(userTextContent);
        userEntity.setSenderType("USER");
        repository.save(userEntity);
    }

    private boolean isHumanHandoff(String userTextContent) {
        String userMsgLower = userTextContent.toLowerCase();
        return userMsgLower.contains("atendente") || userMsgLower.contains("humano") || userMsgLower.contains("suporte");
    }

    private ChatResponseDTO triggerHandoff(UUID sessionId, UUID tenantId) {
        String handoffResponse = "Entendido! Estou transferindo o seu atendimento para um operador humano.";
        saveBotResponse(sessionId, tenantId, handoffResponse);
        return new ChatResponseDTO(handoffResponse, "BOT", true, LocalDateTime.now());
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
}