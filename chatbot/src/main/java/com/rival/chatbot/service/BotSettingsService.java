package com.rival.chatbot.service;

import com.rival.chatbot.domain.BotSettingsEntity;
import com.rival.chatbot.repository.BotSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.UUID;

@Service
public class BotSettingsService {

    private final BotSettingsRepository repository;

    public BotSettingsService(BotSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public BotSettingsEntity getSettingsByTenant(UUID tenantId) {
        if (tenantId == null) {
            return new BotSettingsEntity();
        }
        return repository.findByTenantId(tenantId).orElseGet(() -> {
            BotSettingsEntity defaults = new BotSettingsEntity();
            defaults.setTenantId(tenantId);
            return defaults;
        });
    }

    @Transactional
    public BotSettingsEntity saveSettings(UUID tenantId, BotSettingsEntity incoming) {
        BotSettingsEntity entity = repository.findByTenantId(tenantId).orElseGet(() -> {
            BotSettingsEntity n = new BotSettingsEntity();
            n.setTenantId(tenantId);
            return n;
        });

        if (incoming.getUntrainMessage() != null) entity.setUntrainMessage(incoming.getUntrainMessage().trim());
        if (incoming.getFallbackShortMessage() != null) entity.setFallbackShortMessage(incoming.getFallbackShortMessage().trim());
        if (incoming.getFallbackLongMessage() != null) entity.setFallbackLongMessage(incoming.getFallbackLongMessage().trim());
        if (incoming.getFallbackGenericMessage() != null) entity.setFallbackGenericMessage(incoming.getFallbackGenericMessage().trim());
        if (incoming.getHumanHandoffMessage() != null) entity.setHumanHandoffMessage(incoming.getHumanHandoffMessage().trim());
        if (incoming.getFlowHandoffMessage() != null) entity.setFlowHandoffMessage(incoming.getFlowHandoffMessage().trim());
        if (incoming.getHandoffKeywords() != null) entity.setHandoffKeywords(incoming.getHandoffKeywords().trim());
        if (incoming.getResetFlowKeywords() != null) entity.setResetFlowKeywords(incoming.getResetFlowKeywords().trim());

        return repository.save(entity);
    }

    public boolean matchesHandoffKeyword(UUID tenantId, String message) {
        if (message == null || message.isBlank()) return false;
        String lower = message.toLowerCase().trim();
        BotSettingsEntity settings = getSettingsByTenant(tenantId);
        String kwStr = settings.getHandoffKeywords() != null ? settings.getHandoffKeywords() : "atendente, humano, suporte";

        return Arrays.stream(kwStr.split(","))
                .map(k -> k.trim().toLowerCase())
                .filter(k -> !k.isEmpty())
                .anyMatch(lower::contains);
    }

    public boolean matchesResetKeyword(UUID tenantId, String message) {
        if (message == null || message.isBlank()) return false;
        String lower = message.toLowerCase().trim();
        BotSettingsEntity settings = getSettingsByTenant(tenantId);
        String kwStr = settings.getResetFlowKeywords() != null ? settings.getResetFlowKeywords() : "oi, olá, ola, menu, inicio, início, começar, comecar, start, voltar";

        return Arrays.stream(kwStr.split(","))
                .map(k -> k.trim().toLowerCase())
                .filter(k -> !k.isEmpty())
                .anyMatch(k -> lower.equals(k) || lower.startsWith(k + " "));
    }
}