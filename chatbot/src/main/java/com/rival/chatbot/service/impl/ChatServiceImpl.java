//package com.rival.chatbot.service.impl;
//
//import com.rival.chatbot.domain.ChatMessageEntity;
//import com.rival.chatbot.dto.ChatRequestDTO;
//import com.rival.chatbot.dto.ChatResponseDTO;
//import com.rival.chatbot.mapper.ChatMapper;
//import com.rival.chatbot.repository.ChatMessageRepository;
//import com.rival.chatbot.service.ChatService;
//import com.rival.chatbot.service.DataExtractorService;
//import com.rival.chatbot.service.NlpEngineService;
//import com.rival.chatbot.service.OcrService;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//import org.springframework.web.multipart.MultipartFile;
//
//import java.io.File;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.time.LocalDateTime;
//import java.util.UUID;
//
//@Service
//public class ChatServiceImpl implements ChatService {
//
//    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);
//
//    private final ChatMessageRepository repository;
//    private final ChatMapper chatMapper;
//    private final NlpEngineService nlpEngineService;
//    private final DataExtractorService dataExtractorService;
//    private final OcrService ocrService;
//
//    public ChatServiceImpl(ChatMessageRepository repository,
//                           ChatMapper chatMapper,
//                           NlpEngineService nlpEngineService,
//                           DataExtractorService dataExtractorService,
//                           OcrService ocrService) {
//        this.repository = repository;
//        this.chatMapper = chatMapper;
//        this.nlpEngineService = nlpEngineService;
//        this.dataExtractorService = dataExtractorService;
//        this.ocrService = ocrService;
//    }
//
//    @Override
//    @Transactional
//    public ChatResponseDTO processMessage(ChatRequestDTO request) {
//        String userTextContent = resolveInputContent(request);
//        return handleChatFlow(request.sessionId(), request.tenantId(), userTextContent);
//    }
//
//    @Override
//    @Transactional
//    public ChatResponseDTO processImageFileMessage(UUID sessionId, UUID tenantId, String message, MultipartFile imageFile) {
//        StringBuilder finalContentBuilder = new StringBuilder();
//
//        // 1. Se houver mensagem de texto enviada junto com a imagem, adiciona
//        if (message != null && !message.isBlank()) {
//            finalContentBuilder.append(message.trim()).append(" ");
//        }
//
//        // 2. Processa o arquivo da imagem e faz OCR
//        if (imageFile != null && !imageFile.isEmpty()) {
//            try {
//                log.info("Arquivo de imagem recebido ({}, {} bytes). Processando OCR...",
//                        imageFile.getOriginalFilename(), imageFile.getSize());
//
//                // Salva permanentemente em C:/chatbot_uploads/
//                File savedFile = saveImageToDisk(imageFile);
//                String extractedText = ocrService.extractTextFromImageFile(savedFile);
//
//                if (!extractedText.isBlank()) {
//                    finalContentBuilder.append(extractedText);
//                }
//            } catch (Exception e) {
//                log.error("Erro ao processar arquivo de imagem do usuário", e);
//            }
//        }
//
//        String finalContent = finalContentBuilder.toString().trim();
//        if (finalContent.isBlank()) {
//            finalContent = "Imagem enviada sem texto legível";
//        }
//
//        return handleChatFlow(sessionId, tenantId, finalContent);
//    }
//
//    private ChatResponseDTO handleChatFlow(UUID sessionId, UUID tenantId, String userTextContent) {
//        // 1. Persiste a mensagem do usuário
//        ChatMessageEntity userEntity = new ChatMessageEntity();
//        userEntity.setSessionId(sessionId);
//        userEntity.setTenantId(tenantId);
//        userEntity.setContent(userTextContent);
//        userEntity.setSenderType("USER");
//        repository.save(userEntity);
//
//        // 2. Extração assíncrona de leads
//        dataExtractorService.extractAndSave(sessionId, tenantId, userTextContent);
//
//        // 3. Validação de Transbordo Humano
//        String userMsgLower = userTextContent.toLowerCase();
//        if (userMsgLower.contains("atendente") || userMsgLower.contains("humano") || userMsgLower.contains("suporte")) {
//            String handoffResponse = "Entendido! Estou transferindo o seu atendimento para um operador humano.";
//            saveBotResponse(sessionId, tenantId, handoffResponse);
//            return new ChatResponseDTO(handoffResponse, "BOT", true, LocalDateTime.now());
//        }
//
//        // 4. Execução PNL
//        String nlpResponse = nlpEngineService.processAndMatch(userTextContent);
//
//        // 5. Salva a resposta do robô
//        saveBotResponse(sessionId, tenantId, nlpResponse);
//
//        return new ChatResponseDTO(nlpResponse, "BOT", false, LocalDateTime.now());
//    }
//
//    private String resolveInputContent(ChatRequestDTO request) {
//        if (request.base64Image() != null && !request.base64Image().isBlank()) {
//            log.info("String Base64 detectada. Processando OCR...");
//            String extractedText = ocrService.extractTextFromBase64(request.base64Image());
//            if (!extractedText.isBlank()) {
//                return extractedText;
//            }
//            return "Imagem enviada sem texto legível";
//        }
//        return request.message() != null ? request.message() : "";
//    }
//
//    private void saveBotResponse(UUID sessionId, UUID tenantId, String responseContent) {
//        ChatMessageEntity botEntity = new ChatMessageEntity();
//        botEntity.setSessionId(sessionId);
//        botEntity.setTenantId(tenantId);
//        botEntity.setContent(responseContent);
//        botEntity.setSenderType("BOT");
//        repository.save(botEntity);
//    }
//
//    private File saveImageToDisk(MultipartFile file) throws IOException {
//        String uploadDir = "C:/chatbot_uploads/";
//        File dir = new File(uploadDir);
//        if (!dir.exists()) {
//            dir.mkdirs();
//        }
//
//        String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
//        File serverFile = new File(uploadDir + fileName);
//
//        try (FileOutputStream fos = new FileOutputStream(serverFile)) {
//            fos.write(file.getBytes());
//        }
//        log.info("Imagem armazenada permanentemente em: {}", serverFile.getAbsolutePath());
//        return serverFile;
//    }
//}

//package com.rival.chatbot.service.impl;
//
//import com.rival.chatbot.domain.ChatMessageEntity;
//import com.rival.chatbot.dto.ChatRequestDTO;
//import com.rival.chatbot.dto.ChatResponseDTO;
//import com.rival.chatbot.mapper.ChatMapper;
//import com.rival.chatbot.repository.ChatMessageRepository;
//import com.rival.chatbot.service.ChatService;
//import com.rival.chatbot.service.DataExtractorService;
//import com.rival.chatbot.service.GeminiAiService;
//import com.rival.chatbot.service.OcrService;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//import org.springframework.web.multipart.MultipartFile;
//
//import java.io.File;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.time.LocalDateTime;
//import java.util.UUID;
//
//@Service
//public class ChatServiceImpl implements ChatService {
//
//    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);
//
//    private final ChatMessageRepository repository;
//    private final ChatMapper chatMapper;
//    private final GeminiAiService geminiAiService; // Injeta o Gemini em vez da PNL antiga
//    private final DataExtractorService dataExtractorService;
//    private final OcrService ocrService;
//
//    public ChatServiceImpl(ChatMessageRepository repository,
//                           ChatMapper chatMapper,
//                           GeminiAiService geminiAiService,
//                           DataExtractorService dataExtractorService,
//                           OcrService ocrService) {
//        this.repository = repository;
//        this.chatMapper = chatMapper;
//        this.geminiAiService = geminiAiService;
//        this.dataExtractorService = dataExtractorService;
//        this.ocrService = ocrService;
//    }
//
//    @Override
//    @Transactional
//    public ChatResponseDTO processMessage(ChatRequestDTO request) {
//        String userTextContent = resolveInputContent(request);
//        return handleChatFlow(request.sessionId(), request.tenantId(), userTextContent);
//    }
//
//    @Override
//    @Transactional
//    public ChatResponseDTO processImageFileMessage(UUID sessionId, UUID tenantId, String message, MultipartFile imageFile) {
//        StringBuilder finalContentBuilder = new StringBuilder();
//
//        if (message != null && !message.isBlank()) {
//            finalContentBuilder.append(message.trim()).append(" ");
//        }
//
//        if (imageFile != null && !imageFile.isEmpty()) {
//            try {
//                log.info("Arquivo de imagem recebido ({}, {} bytes). Processando OCR...",
//                        imageFile.getOriginalFilename(), imageFile.getSize());
//
//                File savedFile = saveImageToDisk(imageFile);
//                String extractedText = ocrService.extractTextFromImageFile(savedFile);
//
//                if (!extractedText.isBlank()) {
//                    finalContentBuilder.append(extractedText);
//                }
//            } catch (Exception e) {
//                log.error("Erro ao processar arquivo de imagem do usuário", e);
//            }
//        }
//
//        String finalContent = finalContentBuilder.toString().trim();
//        if (finalContent.isBlank()) {
//            finalContent = "Imagem enviada sem texto legível";
//        }
//
//        return handleChatFlow(sessionId, tenantId, finalContent);
//    }
//
//    private ChatResponseDTO handleChatFlow(UUID sessionId, UUID tenantId, String userTextContent) {
//        // 1. Salva a mensagem do usuário
//        ChatMessageEntity userEntity = new ChatMessageEntity();
//        userEntity.setSessionId(sessionId);
//        userEntity.setTenantId(tenantId);
//        userEntity.setContent(userTextContent);
//        userEntity.setSenderType("USER");
//        repository.save(userEntity);
//
//        // 2. Extração assíncrona de leads
//        dataExtractorService.extractAndSave(sessionId, tenantId, userTextContent);
//
//        // 3. Validação de Transbordo Humano
//        String userMsgLower = userTextContent.toLowerCase();
//        if (userMsgLower.contains("atendente") || userMsgLower.contains("humano") || userMsgLower.contains("suporte")) {
//            String handoffResponse = "Entendido! Estou transferindo o seu atendimento para um operador humano.";
//            saveBotResponse(sessionId, tenantId, handoffResponse);
//            return new ChatResponseDTO(handoffResponse, "BOT", true, LocalDateTime.now());
//        }
//
//        // 4. Chamada da IA Generativa do Google Gemini
//        String aiResponse = geminiAiService.generateResponse(userTextContent);
//
//        // 5. Salva a resposta gerada pela IA
//        saveBotResponse(sessionId, tenantId, aiResponse);
//
//        return new ChatResponseDTO(aiResponse, "BOT", false, LocalDateTime.now());
//    }
//
//    private String resolveInputContent(ChatRequestDTO request) {
//        if (request.base64Image() != null && !request.base64Image().isBlank()) {
//            log.info("String Base64 detectada. Processando OCR...");
//            String extractedText = ocrService.extractTextFromBase64(request.base64Image());
//            if (!extractedText.isBlank()) {
//                return extractedText;
//            }
//            return "Imagem enviada sem texto legível";
//        }
//        return request.message() != null ? request.message() : "";
//    }
//
//    private void saveBotResponse(UUID sessionId, UUID tenantId, String responseContent) {
//        ChatMessageEntity botEntity = new ChatMessageEntity();
//        botEntity.setSessionId(sessionId);
//        botEntity.setTenantId(tenantId);
//        botEntity.setContent(responseContent);
//        botEntity.setSenderType("BOT");
//        repository.save(botEntity);
//    }
//
//    private File saveImageToDisk(MultipartFile file) throws IOException {
//        String uploadDir = "C:/chatbot_uploads/";
//        File dir = new File(uploadDir);
//        if (!dir.exists()) {
//            dir.mkdirs();
//        }
//
//        String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
//        File serverFile = new File(uploadDir + fileName);
//
//        try (FileOutputStream fos = new FileOutputStream(serverFile)) {
//            fos.write(file.getBytes());
//        }
//        log.info("Imagem armazenada permanentemente em: {}", serverFile.getAbsolutePath());
//        return serverFile;
//    }
//}


package com.rival.chatbot.service.impl;

import com.rival.chatbot.domain.ChatMessageEntity;
import com.rival.chatbot.dto.ChatRequestDTO;
import com.rival.chatbot.dto.ChatResponseDTO;
import com.rival.chatbot.mapper.ChatMapper;
import com.rival.chatbot.repository.ChatMessageRepository;
import com.rival.chatbot.service.ChatService;
import com.rival.chatbot.service.DataExtractorService;
import com.rival.chatbot.service.LocalVectorNlpService;
import com.rival.chatbot.service.OcrService;
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

/**
 * Serviço responsável por gerenciar o fluxo principal de mensagens do Chatbot.
 * Integra a recepção de dados, OCR de imagens, análise de PNL vetorial local,
 * captura assíncrona de leads e transbordo para atendimento humano.
 */
@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

    private final ChatMessageRepository repository;
    private final ChatMapper chatMapper;
    private final LocalVectorNlpService localVectorNlpService;
    private final DataExtractorService dataExtractorService;
    private final OcrService ocrService;

    /**
     * Injeção de dependências via construtor.
     * BOAS PRÁTICAS: Favorece a imutabilidade dos atributos (final) e facilita testes unitários com Mocks.
     */
    public ChatServiceImpl(ChatMessageRepository repository,
                           ChatMapper chatMapper,
                           LocalVectorNlpService localVectorNlpService,
                           DataExtractorService dataExtractorService,
                           OcrService ocrService) {
        this.repository = repository;
        this.chatMapper = chatMapper;
        this.localVectorNlpService = localVectorNlpService;
        this.dataExtractorService = dataExtractorService;
        this.ocrService = ocrService;
    }

    /**
     * Processa requisições de chat em formato JSON (Texto puro ou Imagem em Base64).
     */
    @Override
    @Transactional
    public ChatResponseDTO processMessage(ChatRequestDTO request) {
        String userTextContent = resolveInputContent(request);
        return handleChatFlow(request.sessionId(), request.tenantId(), userTextContent);
    }

    /**
     * Processa requisições contendo arquivos físicos de imagem via Multipart.
     */
    @Override
    @Transactional
    public ChatResponseDTO processImageFileMessage(UUID sessionId, UUID tenantId, String message, MultipartFile imageFile) {
        StringBuilder finalContentBuilder = new StringBuilder();

        // 1. Concatena texto opcional enviado junto à imagem
        if (message != null && !message.isBlank()) {
            finalContentBuilder.append(message.trim()).append(" ");
        }

        // 2. Processa o arquivo físico de imagem realizando OCR via Tesseract
        if (imageFile != null && !imageFile.isEmpty()) {
            try {
                log.info("Arquivo de imagem recebido ({}, {} bytes). Processando OCR...",
                        imageFile.getOriginalFilename(), imageFile.getSize());

                File savedFile = saveImageToDisk(imageFile);
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

    /**
     * Executa a orquestração do fluxo de atendimento.
     * BOAS PRÁTICAS:
     * - Garante o registro auditável no banco de TODAS as interações do usuário.
     * - Repassa o 'sessionId' para a PNL vetorial para aplicar a regra anti-repetição por conversa.
     */
    private ChatResponseDTO handleChatFlow(UUID sessionId, UUID tenantId, String userTextContent) {
        // 1. Persiste a mensagem de entrada do usuário para auditoria e histórico
        ChatMessageEntity userEntity = new ChatMessageEntity();
        userEntity.setSessionId(sessionId);
        userEntity.setTenantId(tenantId);
        userEntity.setContent(userTextContent);
        userEntity.setSenderType("USER");
        repository.save(userEntity);

        // 2. Extração assíncrona de dados do lead (CPF, Nome, E-mail) em background
        dataExtractorService.extractAndSave(sessionId, tenantId, userTextContent);

        // 3. Regra de Negócio: Validação e gatilho de Transbordo Humano
        String userMsgLower = userTextContent.toLowerCase();
        if (userMsgLower.contains("atendente") || userMsgLower.contains("humano") || userMsgLower.contains("suporte")) {
            String handoffResponse = "Entendido! Estou transferindo o seu atendimento para um operador humano.";
            saveBotResponse(sessionId, tenantId, handoffResponse);
            return new ChatResponseDTO(handoffResponse, "BOT", true, LocalDateTime.now());
        }

        // 4. Execução do Engine de PNL Vetorial Local
        // BOAS PRÁTICAS: O repasse do 'sessionId' permite consultar a última resposta enviada e alternar opções
        String nlpResponse = localVectorNlpService.processAndMatch(sessionId, userTextContent);

        // 5. Persiste a resposta gerada pelo robô
        saveBotResponse(sessionId, tenantId, nlpResponse);

        return new ChatResponseDTO(nlpResponse, "BOT", false, LocalDateTime.now());
    }

    /**
     * Sanitiza a entrada extraindo o texto puro ou executando OCR a partir do Base64.
     */
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

    /**
     * Persiste a resposta do bot na tabela de mensagens do chat.
     */
    private void saveBotResponse(UUID sessionId, UUID tenantId, String responseContent) {
        ChatMessageEntity botEntity = new ChatMessageEntity();
        botEntity.setSessionId(sessionId);
        botEntity.setTenantId(tenantId);
        botEntity.setContent(responseContent);
        botEntity.setSenderType("BOT");
        repository.save(botEntity);
    }

    /**
     * Armazena arquivos de imagem em disco.
     * BOAS PRÁTICAS:
     * - Utilização de caminhos relativos ('./uploads/') permitindo portabilidade multiplataforma (Windows, Linux, Docker).
     * - Sanitização com 'UUID.randomUUID()' no nome para prevenir sobrescrita de arquivos com mesmo nome.
     */
    private File saveImageToDisk(MultipartFile file) throws IOException {
        String uploadDir = "./uploads/";
        File dir = new File(uploadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        File serverFile = new File(uploadDir + fileName);

        try (FileOutputStream fos = new FileOutputStream(serverFile)) {
            fos.write(file.getBytes());
        }
        log.info("Imagem armazenada permanentemente em: {}", serverFile.getAbsolutePath());
        return serverFile;
    }
}