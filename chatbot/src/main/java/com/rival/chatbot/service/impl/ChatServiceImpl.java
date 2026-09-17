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

@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

    private final ChatMessageRepository repository;
    private final ChatMapper chatMapper;
    private final LocalVectorNlpService localVectorNlpService;
    private final DataExtractorService dataExtractorService;
    private final OcrService ocrService;
    private final AudioTranscriptionService audioTranscriptionService;
    private final OwnGenerativeAiService ownGenerativeAiService;

    public ChatServiceImpl(ChatMessageRepository repository,
                           ChatMapper chatMapper,
                           LocalVectorNlpService localVectorNlpService,
                           DataExtractorService dataExtractorService,
                           OcrService ocrService,
                           AudioTranscriptionService audioTranscriptionService,
                           OwnGenerativeAiService ownGenerativeAiService) {
        this.repository = repository;
        this.chatMapper = chatMapper;
        this.localVectorNlpService = localVectorNlpService;
        this.dataExtractorService = dataExtractorService;
        this.ocrService = ocrService;
        this.audioTranscriptionService = audioTranscriptionService;
        this.ownGenerativeAiService = ownGenerativeAiService;
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
                File savedFile = saveFileToDisk(imageFile.getBytes(), imageFile.getOriginalFilename());
                String extractedText = ocrService.extractTextFromImageFile(savedFile);
                if (!extractedText.isBlank()) finalContentBuilder.append(extractedText);
            } catch (Exception e) {
                log.error("Erro ao processar imagem", e);
            }
        }
        String finalContent = finalContentBuilder.toString().trim();
        if (finalContent.isBlank()) finalContent = "Imagem sem texto legível";

        return handleChatFlow(sessionId, tenantId, finalContent);
    }

    @Override
    @Transactional
    public ChatResponseDTO processAudioFileMessage(UUID sessionId, UUID tenantId, File audioFile) {
        log.info("Processando áudio localmente (Java Puro) na sessão {}", sessionId);

        String transcribedText = audioTranscriptionService.transcribeAudioFile(audioFile);

        // Tratamento da falha do Vosk
        if ("erro ao processar a fala".equals(transcribedText) || "áudio inaudível".equals(transcribedText)) {
            String errorMsg = "Desculpe, não consegui entender o áudio com clareza. Você poderia digitar?";
            saveBotResponse(sessionId, tenantId, errorMsg);
            return new ChatResponseDTO(errorMsg, "BOT", false, LocalDateTime.now());
        }

        log.info("Áudio transcrito: '{}'", transcribedText);

        // Passa para minúscula para facilitar a busca no seu banco de dados PostgreSQL
        return handleChatFlow(sessionId, tenantId, transcribedText.toLowerCase());
    }

    private ChatResponseDTO handleChatFlow(UUID sessionId, UUID tenantId, String userTextContent) {
        // 1. Salva a mensagem do cliente e extrai os dados
        saveUserMessage(sessionId, tenantId, userTextContent);
        dataExtractorService.extractAndSave(sessionId, tenantId, userTextContent);

        // 2. Transbordo Humano
        if (isHumanHandoff(userTextContent)) {
            return triggerHandoff(sessionId, tenantId);
        }

        // 3. Motor Vetorial busca no PostgreSQL e passa para a IA local
        String localContext = localVectorNlpService.processAndMatch(sessionId, userTextContent);
        String finalResponse = ownGenerativeAiService.generateOwnResponse(userTextContent, localContext);

        // 4. Salva a resposta no banco (mesmo se for o caminho de um áudio, fica no histórico)
        saveBotResponse(sessionId, tenantId, finalResponse);

        // 5. A REGRA DE OURO (Mapeamento de Voz): Verifica se a IA/Banco retornou o prefixo "AUDIO:"
        String textResponse = finalResponse;
        String audioUrl = null;

        if (finalResponse != null && finalResponse.startsWith("AUDIO:")) {
            audioUrl = finalResponse.substring(6).trim(); // Remove o prefixo "AUDIO:"
            textResponse = ""; // Zera a resposta de texto para enviar APENAS o arquivo
            log.info("Comando de áudio detectado! O bot vai enviar sua voz nativa: {}", audioUrl);
        }

        return new ChatResponseDTO(textResponse, null, audioUrl, "BOT", false, LocalDateTime.now());
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
        String lower = userTextContent.toLowerCase();
        return lower.contains("atendente") || lower.contains("humano") || lower.contains("suporte");
    }

    private ChatResponseDTO triggerHandoff(UUID sessionId, UUID tenantId) {
        String handoffResponse = "Entendido! Estou transferindo seu atendimento para um operador humano.";
        saveBotResponse(sessionId, tenantId, handoffResponse);
        return new ChatResponseDTO(handoffResponse, "BOT", true, LocalDateTime.now());
    }

    private String resolveInputContent(ChatRequestDTO request) {
        if (request.base64Image() != null && !request.base64Image().isBlank()) {
            String extractedText = ocrService.extractTextFromBase64(request.base64Image());
            return extractedText.isBlank() ? "Imagem sem texto legível" : extractedText;
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
        File dir = new File("./uploads/");
        if (!dir.exists()) dir.mkdirs();
        File serverFile = new File(dir, UUID.randomUUID() + "_" + originalFilename);
        try (FileOutputStream fos = new FileOutputStream(serverFile)) {
            fos.write(bytes);
        }
        return serverFile;
    }
}