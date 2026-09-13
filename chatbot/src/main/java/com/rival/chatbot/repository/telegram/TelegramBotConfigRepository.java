package com.rival.chatbot.repository.telegram;

import com.rival.chatbot.domain.telegram.TelegramBotConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TelegramBotConfigRepository extends JpaRepository<TelegramBotConfigEntity, UUID> {
    Optional<TelegramBotConfigEntity> findByBotUsername(String botUsername);
    Optional<TelegramBotConfigEntity> findFirstByActiveTrue();
}