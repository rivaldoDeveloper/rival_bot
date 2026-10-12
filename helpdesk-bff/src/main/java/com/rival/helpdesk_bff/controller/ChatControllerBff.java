package com.rival.helpdesk_bff.controller;

import com.rival.helpdesk_bff.client.ChatbotClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/chat")
public class ChatControllerBff {

    private final ChatbotClient chatbotClient;

    public ChatControllerBff(ChatbotClient chatbotClient) {
        this.chatbotClient = chatbotClient;
    }

    // O Frontend pede para abrir uma conversa específica
    @GetMapping("/{sessionId}/history")
    public ResponseEntity<Object> getChatHistory(
            @PathVariable UUID sessionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Object history = chatbotClient.getSessionHistory(sessionId, page, size);
        return ResponseEntity.ok(history);
    }
}