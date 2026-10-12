package com.rival.helpdesk_bff.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "chatbot-api", url = "http://localhost:8083")
public interface ChatbotClient {

    // Busca a lista de sessões/conversas ativas na porta 8083
    @GetMapping("/api/v1/chat/history/sessions")
    Object getActiveSessions();
}