package com.rival.chatbot.controller;

import com.rival.chatbot.dto.ChatRequestDTO;
import com.rival.chatbot.dto.ChatResponseDTO;
import com.rival.chatbot.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * Endpoint padrão para envio via JSON (Texto simples ou imagem em Base64)
     */
    @PostMapping("/send")
    public ResponseEntity<ChatResponseDTO> sendMessage(@Valid @RequestBody ChatRequestDTO request) {
        ChatResponseDTO response = chatService.processMessage(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint para envio direto de arquivos de imagem (.png, .jpg, .jpeg) sem precisar de Base64
     */
    @PostMapping(value = "/send-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ChatResponseDTO> sendMessageWithImageFile(
            @RequestParam("sessionId") UUID sessionId,
            @RequestParam("tenantId") UUID tenantId,
            @RequestParam(value = "message", required = false) String message,
            @RequestPart("image") MultipartFile imageFile) {

        ChatResponseDTO response = chatService.processImageFileMessage(sessionId, tenantId, message, imageFile);
        return ResponseEntity.ok(response);
    }
}