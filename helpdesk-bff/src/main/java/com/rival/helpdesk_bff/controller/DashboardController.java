package com.rival.helpdesk_bff.controller;

import com.rival.helpdesk_bff.client.ChatbotClient;
import com.rival.helpdesk_bff.client.UserClient;
import com.rival.helpdesk_bff.dto.DashboardResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.UUID;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);
    private final UserClient userClient;
    private final ChatbotClient chatbotClient;

    public DashboardController(UserClient userClient, ChatbotClient chatbotClient) {
        this.userClient = userClient;
        this.chatbotClient = chatbotClient;
    }

    @GetMapping("/summary/{userId}")
    public ResponseEntity<DashboardResponseDTO> getDashboardSummary(@PathVariable UUID userId) {
        Object user = null;
        Object sessions = Collections.emptyList();

        try {
            user = userClient.getUserById(userId);
        } catch (Exception e) {
            log.warn("Nao foi possivel carregar dados do usuario {}: {}", userId, e.getMessage());
        }

        try {
            sessions = chatbotClient.getActiveSessions();
        } catch (Exception e) {
            log.warn("Nao foi possivel carregar sessoes ativas do Chatbot API: {}", e.getMessage());
        }

        DashboardResponseDTO response = new DashboardResponseDTO(user, sessions);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sessions")
    public ResponseEntity<Object> getActiveSessions() {
        try {
            Object sessions = chatbotClient.getActiveSessions();
            return ResponseEntity.ok(sessions);
        } catch (Exception e) {
            log.error("Erro ao buscar sessoes no Chatbot API via BFF", e);
            return ResponseEntity.ok(Collections.emptyList());
        }
    }
}