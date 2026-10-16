package com.rival.chatbot.service.telegram;

import com.rival.chatbot.domain.telegram.TelegramBotConfigEntity;
import java.util.List;
import java.util.UUID;

public interface TelegramBotConfigService {

    TelegramBotConfigEntity saveOrUpdateBot(TelegramBotConfigEntity botConfig);

    List<TelegramBotConfigEntity> getAllBots();

    /* ========================================================================
     * MÉTODO ADICIONADO PARA RESOLVER O ERRO DE COMPILAÇÃO:
     * Permite ao controller buscar robôs filtrados por Tenant
     * ======================================================================== */
    List<TelegramBotConfigEntity> getBotsByTenant(UUID tenantId);

    void deleteBot(UUID id);
}