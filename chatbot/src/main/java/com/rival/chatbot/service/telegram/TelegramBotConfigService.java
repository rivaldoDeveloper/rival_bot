package com.rival.chatbot.service.telegram;

import com.rival.chatbot.domain.telegram.TelegramBotConfigEntity;

import java.util.List;
import java.util.UUID;

public interface TelegramBotConfigService {
    TelegramBotConfigEntity saveOrUpdateBot(TelegramBotConfigEntity botConfig);
    List<TelegramBotConfigEntity> getAllBots();
    void deleteBot(UUID id);
}