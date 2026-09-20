package com.rival.chatbot.controller;

import com.rival.chatbot.dto.ChatRequestDTO;
import com.rival.chatbot.dto.ChatResponseDTO;
import com.rival.chatbot.service.ChatService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * ENDPOINT ORIGINAL: Processa a mensagem usando apenas o motor de Inteligência (PNL)
     */
    @PostMapping("/send")
    public ResponseEntity<ChatResponseDTO> sendMessage(@Valid @RequestBody ChatRequestDTO request) {
        ChatResponseDTO response = chatService.processMessage(request);
        return ResponseEntity.ok(response);
    }

    /**
     * NOVO ENDPOINT: Processa a mensagem passando pela Árvore de Decisão (Flow Builder)
     */
    @PostMapping("/send-flow")
    public ResponseEntity<ChatResponseDTO> sendFlowMessage(@Valid @RequestBody ChatRequestDTO request) {
        ChatResponseDTO response = chatService.processFlowMessage(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/send-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ChatResponseDTO> sendMessageWithImageFile(
            @RequestParam("sessionId") UUID sessionId,
            @RequestParam("tenantId") UUID tenantId,
            @RequestParam(value = "message", required = false) String message,
            @RequestPart("image") MultipartFile imageFile) {

        ChatResponseDTO response = chatService.processImageFileMessage(sessionId, tenantId, message, imageFile);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/send-audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> sendMessageWithAudioFile(
            @RequestParam("sessionId") UUID sessionId,
            @RequestParam("tenantId") UUID tenantId,
            @RequestParam("file") MultipartFile file) {

        try {
            if (file == null || file.isEmpty()) {
                log.warn("Arquivo de áudio enviado está vazio.");
                return ResponseEntity.badRequest().body("O arquivo de áudio não foi fornecido.");
            }

            // Define o diretório de upload com caminho absoluto para evitar erros do Tomcat
            File tempDir = new File("uploads").getCanonicalFile();
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }

            File tempFile = new File(tempDir, UUID.randomUUID() + "_" + file.getOriginalFilename());
            file.transferTo(tempFile);

            log.info("Arquivo de áudio salvo com sucesso em: {}", tempFile.getAbsolutePath());

            ChatResponseDTO response = chatService.processAudioFileMessage(sessionId, tenantId, tempFile);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erro ao processar envio de áudio via API REST: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro interno: " + e.getMessage());
        }
    }
}