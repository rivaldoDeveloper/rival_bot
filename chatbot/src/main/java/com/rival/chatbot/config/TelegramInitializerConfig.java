package com.rival.chatbot.config;

import com.rival.chatbot.service.telegram.TelegramBotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Configuration
public class TelegramInitializerConfig {

    private static final Logger log = LoggerFactory.getLogger(TelegramInitializerConfig.class);

    @Bean
    public TelegramBotsApi telegramBotsApi(TelegramBotService telegramBotService) throws TelegramApiException {
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        try {
            botsApi.registerBot(telegramBotService);
            log.info("Telegram Bot [{}] registrado e escutando mensagens em tempo real!", telegramBotService.getBotUsername());
        } catch (TelegramApiException e) {
            log.error("Erro ao registrar o Bot do Telegram", e);
        }
        return botsApi;
    }
}