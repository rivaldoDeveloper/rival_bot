package com.rival.chatbot.repository;

import com.rival.chatbot.domain.FlowConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FlowConfigRepository extends JpaRepository<FlowConfigEntity, UUID> {

    /**
     * Procura o fluxo visual que está atualmente ativo para um determinado Tenant.
     * Retorna um Optional vazio caso a empresa não tenha configurado um fluxo.
     */
    Optional<FlowConfigEntity> findFirstByTenantIdAndActiveTrue(UUID tenantId);

}