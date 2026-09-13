package com.rival.chatbot.service.telegram.impl;

import com.rival.chatbot.domain.telegram.TelegramBotConfigEntity;
import com.rival.chatbot.repository.telegram.TelegramBotConfigRepository;
import com.rival.chatbot.service.telegram.TelegramBotConfigService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TelegramBotConfigServiceImpl implements TelegramBotConfigService {

    private final TelegramBotConfigRepository repository;

    public TelegramBotConfigServiceImpl(TelegramBotConfigRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public TelegramBotConfigEntity saveOrUpdateBot(TelegramBotConfigEntity botConfig) {
        return repository.findByBotUsername(botConfig.getBotUsername())
                .map(existing -> {
                    existing.setBotToken(botConfig.getBotToken());
                    existing.setActive(botConfig.isActive());

                    if (botConfig.getTenantId() != null) {
                        existing.setTenantId(botConfig.getTenantId());
                    } else if (existing.getTenantId() == null) {
                        existing.setTenantId(UUID.randomUUID());
                    }

                    return repository.save(existing);
                })
                .orElseGet(() -> {
                    if (botConfig.getTenantId() == null) {
                        botConfig.setTenantId(UUID.randomUUID());
                    }
                    return repository.save(botConfig);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<TelegramBotConfigEntity> getAllBots() {
        return repository.findAll();
    }
}