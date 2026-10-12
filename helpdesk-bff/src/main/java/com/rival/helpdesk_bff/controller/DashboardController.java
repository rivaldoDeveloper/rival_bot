package com.rival.helpdesk_bff.controller;

import com.rival.helpdesk_bff.client.ChatbotClient;
import com.rival.helpdesk_bff.client.UserClient;
import com.rival.helpdesk_bff.dto.DashboardResponseDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final UserClient userClient;
    private final ChatbotClient chatbotClient;

    public DashboardController(UserClient userClient, ChatbotClient chatbotClient) {
        this.userClient = userClient;
        this.chatbotClient = chatbotClient;
    }

    // O Frontend chama ISTO UMA ÚNICA VEZ e recebe tudo que precisa para desenhar o ecrã inicial!
    @GetMapping("/summary/{userId}")
    public ResponseEntity<DashboardResponseDTO> getDashboardSummary(@PathVariable UUID userId) {
        Object user = userClient.getUserById(userId);
        Object sessions = chatbotClient.getActiveSessions();

        DashboardResponseDTO response = new DashboardResponseDTO(user, sessions);
        return ResponseEntity.ok(response);
    }
}