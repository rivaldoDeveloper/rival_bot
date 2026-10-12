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
@RequestMapping("/dashboard") // O caminho completo será /api/bff/dashboard
public class DashboardController {

    private final UserClient userClient;
    private final ChatbotClient chatbotClient;

    public DashboardController(UserClient userClient, ChatbotClient chatbotClient) {
        this.userClient = userClient;
        this.chatbotClient = chatbotClient;
    }

    @GetMapping("/summary/{userId}")
    public ResponseEntity<DashboardResponseDTO> getDashboardSummary(@PathVariable UUID userId) {

        // 1. O BFF vai até a cozinha do User Service pegar os dados do operador
        Object user = userClient.getUserById(userId);

        // 2. O BFF vai até a cozinha do Chatbot pegar a fila de mensagens
        Object sessions = chatbotClient.getActiveSessions();

        // 3. O BFF junta tudo na mesma bandeja (DTO) e entrega ao Angular
        DashboardResponseDTO response = new DashboardResponseDTO(user, sessions);

        return ResponseEntity.ok(response);
    }
}