package com.rival.chatbot.repository;

import com.rival.chatbot.domain.BotSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BotSettingsRepository extends JpaRepository<BotSettingsEntity, UUID> {
    Optional<BotSettingsEntity> findByTenantId(UUID tenantId);
}