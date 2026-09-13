package com.rival.chatbot.service.telegram;

import com.rival.chatbot.domain.telegram.TelegramBotConfigEntity;

import java.util.List;

public interface TelegramBotConfigService {
    TelegramBotConfigEntity saveOrUpdateBot(TelegramBotConfigEntity botConfig);
    List<TelegramBotConfigEntity> getAllBots();
}