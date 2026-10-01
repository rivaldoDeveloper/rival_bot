package com.rival.chatbot.config;

import com.rival.chatbot.domain.telegram.TelegramBotConfigEntity;
import com.rival.chatbot.repository.CustomerDataRepository;
import com.rival.chatbot.repository.telegram.TelegramBotConfigRepository;
import com.rival.chatbot.service.ChatService;
import com.rival.chatbot.service.telegram.TelegramBotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class TelegramInitializerConfig {

    private static final Logger log = LoggerFactory.getLogger(TelegramInitializerConfig.class);

    // ✅ NOVO: Mapa para guardar a ligação de todos os robôs criados pelo Angular
    public static final Map<String, TelegramBotService> ACTIVE_BOTS = new HashMap<>();

    @Bean
    public TelegramBotsApi telegramBotsApi(TelegramBotConfigRepository botConfigRepository,
                                           ChatService chatService,
                                           CustomerDataRepository customerDataRepository) throws TelegramApiException {

        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);

        // Pega em TODOS os robôs ativos no banco de dados
        List<TelegramBotConfigEntity> activeBots = botConfigRepository.findAll().stream()
                .filter(TelegramBotConfigEntity::isActive)
                .toList();

        if (activeBots.isEmpty()) {
            log.warn("Nenhum Bot de Telegram configurado. Registe um no painel.");
            return botsApi;
        }

        // Liga e regista cada robô um por um
        for (TelegramBotConfigEntity config : activeBots) {
            try {
                if (config.getBotToken() != null && !config.getBotToken().isBlank()) {
                    TelegramBotService newBotInstance = new TelegramBotService(chatService, customerDataRepository, config);
                    botsApi.registerBot(newBotInstance);
                    ACTIVE_BOTS.put(config.getBotUsername(), newBotInstance);
                    log.info("Telegram Bot [@{}] inicializado e a escutar!", config.getBotUsername());
                }
            } catch (TelegramApiException e) {
                log.error("Erro ao iniciar o Bot: {}", config.getBotUsername(), e);
            }
        }

        return botsApi;
    }
}