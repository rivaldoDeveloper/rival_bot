package com.rival.chatbot.service.impl;

import com.rival.chatbot.domain.ChatMessageEntity;
import com.rival.chatbot.domain.CustomerDataEntity;
import com.rival.chatbot.dto.ChatRequestDTO;
import com.rival.chatbot.dto.ChatResponseDTO;
import com.rival.chatbot.mapper.ChatMapper;
import com.rival.chatbot.repository.ChatMessageRepository;
import com.rival.chatbot.repository.CustomerDataRepository;
import com.rival.chatbot.repository.whatsapp.WhatsAppAccountRepository;
import com.rival.chatbot.service.*;
import com.rival.chatbot.service.telegram.TelegramBotService;
import com.rival.chatbot.service.whatsapp.WhatsAppSenderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
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
    private final FlowEngineService flowEngineService;

    // Dependências para Disparo Bidirecional
    private final CustomerDataRepository customerDataRepository;
    private final WhatsAppSenderService whatsAppSenderService;
    private final WhatsAppAccountRepository whatsAppAccountRepository;
    private TelegramBotService telegramBotService;

    public ChatServiceImpl(ChatMessageRepository repository,
                           ChatMapper chatMapper,
                           LocalVectorNlpService localVectorNlpService,
                           DataExtractorService dataExtractorService,
                           OcrService ocrService,
                           AudioTranscriptionService audioTranscriptionService,
                           OwnGenerativeAiService ownGenerativeAiService,
                           FlowEngineService flowEngineService,
                           CustomerDataRepository customerDataRepository,
                           WhatsAppSenderService whatsAppSenderService,
                           WhatsAppAccountRepository whatsAppAccountRepository) {
        this.repository = repository;
        this.chatMapper = chatMapper;
        this.localVectorNlpService = localVectorNlpService;
        this.dataExtractorService = dataExtractorService;
        this.ocrService = ocrService;
        this.audioTranscriptionService = audioTranscriptionService;
        this.ownGenerativeAiService = ownGenerativeAiService;
        this.flowEngineService = flowEngineService;
        this.customerDataRepository = customerDataRepository;
        this.whatsAppSenderService = whatsAppSenderService;
        this.whatsAppAccountRepository = whatsAppAccountRepository;
    }

    // Injeção Tardia (Lazy) para evitar Dependência Circular
    @Autowired
    public void setTelegramBotService(@Lazy TelegramBotService telegramBotService) {
        this.telegramBotService = telegramBotService;
    }

    @Override
    @Transactional
    public ChatResponseDTO processMessage(ChatRequestDTO request) {
        String userTextContent = resolveInputContent(request);
        return handleStandardChat(request.sessionId(), request.tenantId(), userTextContent, request.channel(), request.externalId());
    }

    @Override
    @Transactional
    public ChatResponseDTO processFlowMessage(ChatRequestDTO request) {
        String userTextContent = resolveInputContent(request);
        return handleVisualFlowChat(request.sessionId(), request.tenantId(), userTextContent, request.channel(), request.externalId());
    }

    @Override
    @Transactional
    public ChatResponseDTO processAgentMessage(ChatRequestDTO request) {
        // 1. Grava no banco de dados para a interface Angular
        saveBotResponse(request.sessionId(), request.tenantId(), request.message(), "AGENT");

        // 2. Localiza o cliente para efetuar o disparo e atualizar o status
        customerDataRepository.findBySessionId(request.sessionId()).ifPresent(customer -> {
            customer.setUpdatedAt(LocalDateTime.now());
            customerDataRepository.save(customer);

            String externalId = customer.getExternalId();
            if (externalId == null || externalId.isBlank()) {
                log.warn("Sessão {} sem externalId associado. Impossível enviar mensagem.", request.sessionId());
                return;
            }

            // 3. Efetua o disparo de texto puro
            if ("WHATSAPP".equals(customer.getChannel())) {
                whatsAppAccountRepository.findAll().stream()
                        .filter(acc -> acc.getTenantId().equals(request.tenantId()))
                        .findFirst()
                        .ifPresent(acc -> whatsAppSenderService.sendMessage(acc.getInstanceName(), acc.getEvolutionApiKey(), externalId, request.message()));
            } else if ("TELEGRAM".equals(customer.getChannel())) {
                telegramBotService.sendMessageToClient(externalId, request.message());
            }
        });

        return new ChatResponseDTO(request.message(), null, null, "AGENT", false, LocalDateTime.now());
    }

    @Override
    @Transactional
    public ChatResponseDTO processAgentMediaMessage(UUID sessionId, UUID tenantId, String message, MultipartFile file) {
        try {
            // Guarda o arquivo na pasta uploads local
            File savedFile = saveFileToDisk(file.getBytes(), file.getOriginalFilename());

            // Cria o link que o Angular vai reconhecer e renderizar na bolha
            String fileUrl = "http://localhost:8080/uploads/" + savedFile.getName();
            String contentToSave = (message != null && !message.isBlank()) ? message + "\n" + fileUrl : fileUrl;

            saveBotResponse(sessionId, tenantId, contentToSave, "AGENT");

            // Localiza o cliente e envia o anexo real via API apropriada
            customerDataRepository.findBySessionId(sessionId).ifPresent(customer -> {
                customer.setUpdatedAt(LocalDateTime.now());
                customerDataRepository.save(customer);

                String externalId = customer.getExternalId();
                if (externalId != null && !externalId.isBlank()) {
                    if ("WHATSAPP".equals(customer.getChannel())) {
                        whatsAppAccountRepository.findAll().stream()
                                .filter(acc -> acc.getTenantId().equals(tenantId))
                                .findFirst()
                                .ifPresent(acc -> whatsAppSenderService.sendMediaMessage(
                                        acc.getInstanceName(), acc.getEvolutionApiKey(), externalId, message, savedFile));
                    } else if ("TELEGRAM".equals(customer.getChannel())) {
                        // ✅ Chamada corrigida para a função sendMediaToClient no TelegramBotService
                        telegramBotService.sendMediaToClient(externalId, message, savedFile);
                    }
                }
            });

            return new ChatResponseDTO(contentToSave, null, null, "AGENT", false, LocalDateTime.now());
        } catch (Exception e) {
            log.error("Erro processando envio de mídia pelo Agente", e);
            throw new RuntimeException("Falha ao processar arquivo do agente.", e);
        }
    }

    @Override
    @Transactional
    public ChatResponseDTO processImageFileMessage(UUID sessionId, UUID tenantId, String message, MultipartFile imageFile) {
        StringBuilder finalContentBuilder = new StringBuilder();

        if (message != null && !message.isBlank()) {
            finalContentBuilder.append(message.trim()).append("\n");
        }

        if (imageFile != null && !imageFile.isEmpty()) {
            try {
                // Guarda a imagem que veio do celular para o Angular ver
                File savedFile = saveFileToDisk(imageFile.getBytes(), imageFile.getOriginalFilename());
                String fileUrl = "http://localhost:8080/uploads/" + savedFile.getName();
                finalContentBuilder.append(fileUrl);
            } catch (Exception e) {
                log.error("Erro ao processar imagem", e);
            }
        }

        String finalContent = finalContentBuilder.toString().trim();
        if (finalContent.isBlank()) finalContent = "Imagem não pôde ser guardada.";

        return handleStandardChat(sessionId, tenantId, finalContent, "WHATSAPP", null);
    }

    @Override
    @Transactional
    public ChatResponseDTO processAudioFileMessage(UUID sessionId, UUID tenantId, File audioFile) {
        // Gera o link do áudio para aparecer no painel
        String fileUrl = "http://localhost:8080/uploads/" + audioFile.getName();
        return handleStandardChat(sessionId, tenantId, fileUrl, "WHATSAPP", null);
    }

    private ChatResponseDTO handleStandardChat(UUID sessionId, UUID tenantId, String userTextContent, String channel, String externalId) {
        saveUserMessage(sessionId, tenantId, userTextContent);
        dataExtractorService.extractAndSave(sessionId, tenantId, userTextContent, channel, externalId);

        if (isHumanHandoff(userTextContent)) return triggerHandoff(sessionId, tenantId);

        String localContext = localVectorNlpService.processAndMatch(sessionId, userTextContent);
        String finalResponse = ownGenerativeAiService.generateOwnResponse(userTextContent, localContext);

        saveBotResponse(sessionId, tenantId, finalResponse, "BOT");
        return checkAudioCommandAndReturn(finalResponse);
    }

    private ChatResponseDTO handleVisualFlowChat(UUID sessionId, UUID tenantId, String userTextContent, String channel, String externalId) {
        saveUserMessage(sessionId, tenantId, userTextContent);
        dataExtractorService.extractAndSave(sessionId, tenantId, userTextContent, channel, externalId);

        if (isHumanHandoff(userTextContent)) return triggerHandoff(sessionId, tenantId);

        String flowResponse = flowEngineService.processFlow(sessionId, tenantId, userTextContent);
        String finalResponse = (flowResponse != null) ? flowResponse : ownGenerativeAiService.generateOwnResponse(userTextContent, localVectorNlpService.processAndMatch(sessionId, userTextContent));

        saveBotResponse(sessionId, tenantId, finalResponse, "BOT");
        return checkAudioCommandAndReturn(finalResponse);
    }

    // ==========================================
    // MÉTODOS AUXILIARES
    // ==========================================

    private void saveUserMessage(UUID sessionId, UUID tenantId, String userTextContent) {
        ChatMessageEntity userEntity = new ChatMessageEntity();
        userEntity.setSessionId(sessionId);
        userEntity.setTenantId(tenantId);
        userEntity.setContent(userTextContent);
        userEntity.setSenderType("USER");
        repository.save(userEntity);
    }

    private void saveBotResponse(UUID sessionId, UUID tenantId, String responseContent, String senderType) {
        ChatMessageEntity botEntity = new ChatMessageEntity();
        botEntity.setSessionId(sessionId);
        botEntity.setTenantId(tenantId);
        botEntity.setContent(responseContent);
        botEntity.setSenderType(senderType);
        repository.save(botEntity);
    }

    private boolean isHumanHandoff(String userTextContent) {
        String lower = userTextContent.toLowerCase();
        return lower.contains("atendente") || lower.contains("humano") || lower.contains("suporte");
    }

    private ChatResponseDTO triggerHandoff(UUID sessionId, UUID tenantId) {
        String handoffResponse = "Entendido! Estou transferindo seu atendimento para um operador humano.";
        saveBotResponse(sessionId, tenantId, handoffResponse, "BOT");
        return new ChatResponseDTO(handoffResponse, "BOT", true, LocalDateTime.now());
    }

    private String resolveInputContent(ChatRequestDTO request) {
        return request.message() != null ? request.message() : "";
    }

    private ChatResponseDTO checkAudioCommandAndReturn(String finalResponse) {
        String textResponse = finalResponse;
        String audioUrl = null;

        if (finalResponse != null && finalResponse.startsWith("AUDIO:")) {
            audioUrl = finalResponse.substring(6).trim();
            textResponse = "";
            log.info("Comando de áudio detectado: {}", audioUrl);
        }

        return new ChatResponseDTO(textResponse, null, audioUrl, "BOT", false, LocalDateTime.now());
    }

    // ✅ CORREÇÃO: Limpeza profunda do nome do ficheiro (remove acentos, espaços e acentuação)
    private File saveFileToDisk(byte[] bytes, String originalFilename) throws IOException {
        File dir = new File("./uploads/");
        if (!dir.exists()) dir.mkdirs();

        String safeName = "media.bin";
        if (originalFilename != null) {
            // Converte para letras minúsculas, troca espaços por underscore, e tira acentos e cedilhas.
            safeName = java.text.Normalizer.normalize(originalFilename, java.text.Normalizer.Form.NFD)
                    .replaceAll("[^\\p{ASCII}]", "")
                    .replaceAll("\\s+", "_")
                    .replaceAll("[^a-zA-Z0-9\\.\\-]", "_")
                    .toLowerCase();
        }

        File serverFile = new File(dir, UUID.randomUUID().toString().substring(0, 8) + "_" + safeName);

        try (FileOutputStream fos = new FileOutputStream(serverFile)) {
            fos.write(bytes);
        }
        return serverFile;
    }

    @Override
    @Transactional
    public void deleteMessage(UUID id) {
        repository.deleteById(id);
    }

    @Override
    @Transactional
    public ChatResponseDTO editMessage(UUID id, String newContent) {
        ChatMessageEntity msg = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Mensagem não encontrada"));
        msg.setContent(newContent);
        repository.save(msg);
        return new ChatResponseDTO(newContent, null, null, msg.getSenderType(), false, msg.getCreatedAt());
    }
}