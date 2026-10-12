package com.rival.helpdesk_bff.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(name = "chatbot-api", url = "http://localhost:8083")
public interface ChatbotClient {

    // Lista as conversas ativas no momento
    @GetMapping("/api/v1/chat/history/sessions")
    Object getActiveSessions();

    // Traz o histórico de mensagens de uma sessão específica
    @GetMapping("/api/v1/chat/history/{sessionId}")
    Object getSessionHistory(@PathVariable("sessionId") UUID sessionId,
                             @RequestParam(value = "page", defaultValue = "0") int page,
                             @RequestParam(value = "size", defaultValue = "50") int size);
}