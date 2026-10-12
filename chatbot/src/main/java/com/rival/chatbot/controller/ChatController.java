package com.rival.chatbot.controller;

import com.rival.chatbot.dto.ChatRequestDTO;
import com.rival.chatbot.dto.ChatResponseDTO;
import com.rival.chatbot.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Chat Operations", description = "Endpoints para envio e processamento de mensagens no chatbot")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @Operation(summary = "Enviar mensagem simples", description = "Processa uma mensagem de texto padrao do usuario.")
    @PostMapping("/send")
    public ResponseEntity<ChatResponseDTO> sendMessage(@Valid @RequestBody ChatRequestDTO request) {
        ChatResponseDTO response = chatService.processMessage(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Enviar mensagem via Flow", description = "Processa uma mensagem do usuario usando o motor de fluxos visuais.")
    @PostMapping("/send-flow")
    public ResponseEntity<ChatResponseDTO> sendFlowMessage(@Valid @RequestBody ChatRequestDTO request) {
        ChatResponseDTO response = chatService.processFlowMessage(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Enviar mensagem com imagem", description = "Processa uma mensagem com imagem em anexo enviada pelo usuario.")
    @PostMapping(value = "/send-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ChatResponseDTO> sendMessageWithImageFile(
            @RequestParam("sessionId") UUID sessionId,
            @RequestParam("tenantId") UUID tenantId,
            @RequestParam(value = "message", required = false) String message,
            @RequestPart("image") MultipartFile imageFile) {
        ChatResponseDTO response = chatService.processImageFileMessage(sessionId, tenantId, message, imageFile);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Enviar mensagem de audio", description = "Processa uma mensagem de audio enviada pelo usuario.")
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

    @Operation(summary = "Agente responde", description = "Envia uma mensagem de texto a partir do operador humano.")
    @PostMapping("/send-agent")
    public ResponseEntity<ChatResponseDTO> sendAgentMessage(@Valid @RequestBody ChatRequestDTO request) {
        ChatResponseDTO response = chatService.processAgentMessage(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Agente envia midia", description = "Envia uma mensagem com anexo (imagem, video, arquivo) a partir do operador humano.")
    @PostMapping(value = "/send-agent-media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ChatResponseDTO> sendAgentMediaMessage(
            @RequestParam("sessionId") UUID sessionId,
            @RequestParam("tenantId") UUID tenantId,
            @RequestParam(value = "message", required = false) String message,
            @RequestPart("file") MultipartFile file) {
        ChatResponseDTO response = chatService.processAgentMediaMessage(sessionId, tenantId, message, file);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Deletar mensagem", description = "Remove uma mensagem especifica do historico.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMessage(@PathVariable UUID id) {
        chatService.deleteMessage(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Editar mensagem", description = "Altera o conteudo de uma mensagem existente.")
    @PutMapping("/{id}")
    public ResponseEntity<ChatResponseDTO> editMessage(@PathVariable UUID id, @RequestBody java.util.Map<String, String> payload) {
        String newContent = payload.get("content");
        return ResponseEntity.ok(chatService.editMessage(id, newContent));
    }

    @Operation(summary = "Alternar IA", description = "Liga ou desliga o controle da Inteligencia Artificial para uma sessao especifica.")
    @PutMapping("/toggle-ai/{sessionId}")
    public ResponseEntity<Void> toggleAiMode(@PathVariable UUID sessionId, @RequestBody java.util.Map<String, Boolean> payload) {
        Boolean isAiActive = payload.get("isAiActive");
        chatService.toggleAiMode(sessionId, isAiActive != null ? isAiActive : true);
        return ResponseEntity.ok().build();
    }
}