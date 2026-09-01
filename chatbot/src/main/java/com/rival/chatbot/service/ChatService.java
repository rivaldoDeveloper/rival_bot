package com.rival.chatbot.service;

import com.rival.chatbot.dto.ChatRequestDTO;
import com.rival.chatbot.dto.ChatResponseDTO;

public interface ChatService {
    ChatResponseDTO processMessage(ChatRequestDTO chatRequestDTO);
}
