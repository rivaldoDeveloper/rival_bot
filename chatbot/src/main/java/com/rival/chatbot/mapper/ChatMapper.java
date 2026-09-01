package com.rival.chatbot.mapper;

import com.rival.chatbot.domain.ChatMessageEntity;
import com.rival.chatbot.dto.ChatRequestDTO;
import org.springframework.stereotype.Component;

@Component
public class ChatMapper {

    public ChatMessageEntity toEntity(ChatRequestDTO dto) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setSessionId(dto.sessionId());
        entity.setTenantId(dto.tenantId());
        entity.setContent(dto.message());
        entity.setSenderType("USER");
        return entity;
    }
}
