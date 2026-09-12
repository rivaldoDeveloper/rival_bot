package com.rival.chatbot.repository;

import com.rival.chatbot.domain.ChatMessageEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, UUID> {

    // Método legado mantido para compatibilidade
    List<ChatMessageEntity> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    // Método paginado: busca as mensagens de uma sessão ordenadas por data de criação
    Page<ChatMessageEntity> findBySessionId(UUID sessionId, Pageable pageable);
}