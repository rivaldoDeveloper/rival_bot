package com.rival.chatbot.controller;

import com.rival.chatbot.domain.ChatMessageEntity;
import com.rival.chatbot.repository.ChatMessageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat/history")
public class ChatHistoryController {

    private final ChatMessageRepository chatMessageRepository;

    public ChatHistoryController(ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
    }

    /**
     * Endpoint Paginado para consultar histórico de conversas.
     * Exemplo de uso: GET /api/v1/chat/history/f47ac10b-58cc-4372-a567-0e02b2c3d479?page=0&size=20
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<Page<ChatMessageEntity>> getHistoryBySessionId(
            @PathVariable UUID sessionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // Ordena por data de criação decrescente (mensagens mais recentes primeiro)
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<ChatMessageEntity> messages = chatMessageRepository.findBySessionId(sessionId, pageable);

        return ResponseEntity.ok(messages);
    }
}