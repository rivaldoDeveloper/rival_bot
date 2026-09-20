package com.rival.chatbot.service.impl;

import com.rival.chatbot.domain.ChatMessageEntity;
import com.rival.chatbot.dto.ChatRequestDTO;
import com.rival.chatbot.dto.ChatResponseDTO;
import com.rival.chatbot.mapper.ChatMapper;
import com.rival.chatbot.repository.ChatMessageRepository;
import com.rival.chatbot.service.*;
import com.rival.chatbot.service.AudioTranscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

//@Service
@SuppressWarnings({"all", "java:S1186", "java:S1192"})
public class ChatLegacyAudioServiceImpl implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

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
        return handleChatFlow(request.sessionId(), request.tenantId(), userTextContent);
    }

    @Override
    @Transactional
    public ChatResponseDTO processImageFileMessage(UUID sessionId, UUID tenantId, String message, MultipartFile imageFile) {
        StringBuilder finalContentBuilder = new StringBuilder();

        if (message != null && !message.isBlank()) {
            finalContentBuilder.append(message.trim()).append(" ");
        }

        if (imageFile != null && !imageFile.isEmpty()) {
            try {
                log.info("Arquivo de imagem recebido ({}, {} bytes). Processando OCR...",
                        imageFile.getOriginalFilename(), imageFile.getSize());
                File savedFile = saveFileToDisk(imageFile.getBytes(), imageFile.getOriginalFilename());
                String extractedText = ocrService.extractTextFromImageFile(savedFile);
                if (!extractedText.isBlank()) {
                    finalContentBuilder.append(extractedText);
                }
            } catch (Exception e) {
                log.error("Erro ao processar arquivo de imagem do usuário", e);
            }
        }

        String finalContent = finalContentBuilder.toString().trim();
        if (finalContent.isBlank()) {
            finalContent = "Imagem enviada sem texto legível";
        }

        return handleChatFlow(sessionId, tenantId, finalContent);
    }

    @Override
    public ChatResponseDTO processFlowMessage(ChatRequestDTO chatRequestDTO) {
        return null;
    }

    @Override
    @Transactional
    public ChatResponseDTO processAudioFileMessage(UUID sessionId, UUID tenantId, File audioFile) {
        log.info("Processando áudio recebido para a sessão {}", sessionId);
        String transcribedText = audioTranscriptionService.transcribeAudioFile(audioFile);

        if (transcribedText == null || transcribedText.isBlank()) {
            transcribedText = "Áudio enviado sem fala legível";
        }

        return handleChatFlow(sessionId, tenantId, transcribedText);
    }

    private ChatResponseDTO handleChatFlow(UUID sessionId, UUID tenantId, String userTextContent) {
        // 1. Salva mensagem de entrada do usuário no banco
        saveUserMessage(sessionId, tenantId, userTextContent);

        // 2. Extração assíncrona de dados de lead (CPF, Nome, E-mail)
        dataExtractorService.extractAndSave(sessionId, tenantId, userTextContent);

        // 3. Regra de Transbordo Humano
        if (isHumanHandoff(userTextContent)) {
            return triggerHandoff(sessionId, tenantId);
        }

        // 4. Consulta a base de conhecimento/intenções local (RAG)
        String localContext = localVectorNlpService.processAndMatch(sessionId, userTextContent);

        // 5. Monta o prompt combinando contexto local + mensagem do usuário para o Gemini
        String finalPrompt = userTextContent;
        if (localContext != null && !localContext.contains("Desculpe, não consegui entender")) {
            finalPrompt = "Com base nas seguintes informações da empresa: " + localContext + "\n\nResponda à solicitação do cliente: " + userTextContent;
        }

        log.info("Enviando requisição para processamento no Gemini...");
        String finalResponse = geminiAiService.generateResponse(finalPrompt);

        // 6. Salva a resposta do robô e retorna
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
        if (request.base64Image() != null && !request.base64Image().isBlank()) {
            log.info("String Base64 detectada. Processando OCR...");
            String extractedText = ocrService.extractTextFromBase64(request.base64Image());
            if (!extractedText.isBlank()) {
                return extractedText;
            }
            return "Imagem enviada sem texto legível";
        }
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

    private File saveFileToDisk(byte[] bytes, String originalFilename) throws IOException {
        String uploadDir = "./uploads/";
        File dir = new File(uploadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String fileName = UUID.randomUUID() + "_" + originalFilename;
        File serverFile = new File(uploadDir + fileName);
        try (FileOutputStream fos = new FileOutputStream(serverFile)) {
            fos.write(bytes);
        }
        return serverFile;
    }
}