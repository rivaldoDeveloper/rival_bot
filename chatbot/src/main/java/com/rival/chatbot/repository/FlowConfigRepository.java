package com.rival.chatbot.repository;

import com.rival.chatbot.domain.FlowConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FlowConfigRepository extends JpaRepository<FlowConfigEntity, UUID> {

    /**
     * Procura o fluxo visual que está atualmente ativo para um determinado Tenant.
     * Retorna um Optional vazio caso a empresa não tenha configurado um fluxo.
     */
    Optional<FlowConfigEntity> findFirstByTenantIdAndChannelAndActiveTrue(UUID tenantId, String channel);

    // Usado pelo Painel Angular (Carrega o fluxo específico do canal)
    Optional<FlowConfigEntity> findFirstByTenantIdAndChannel(UUID tenantId, String channel);

    // ✅ NOVO MÉTODO: Retorna todas as linhas para que o Controller possa apagar as duplicatas
    List<FlowConfigEntity> findAllByTenantIdAndChannel(UUID tenantId, String channel);
}