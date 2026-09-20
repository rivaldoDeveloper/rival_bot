package com.rival.chatbot.controller;

import com.rival.chatbot.domain.FlowConfigEntity;
import com.rival.chatbot.repository.FlowConfigRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/flow")
public class FlowConfigController {

    private final FlowConfigRepository flowConfigRepository;

    public FlowConfigController(FlowConfigRepository flowConfigRepository) {
        this.flowConfigRepository = flowConfigRepository;
    }

    /**
     * Endpoint para o Frontend salvar o fluxo desenhado no React Flow.
     * Recebe o JSON gerado pelos blocos e guarda no PostgreSQL.
     */
    @PostMapping("/save")
    public ResponseEntity<FlowConfigEntity> saveFlow(@RequestBody FlowConfigEntity flowConfig) {
        // Verifica se a empresa (tenant) já tem um fluxo ativo para atualizar, senão cria um novo
        Optional<FlowConfigEntity> existingFlow = flowConfigRepository.findFirstByTenantIdAndActiveTrue(flowConfig.getTenantId());

        if (existingFlow.isPresent()) {
            FlowConfigEntity entityToUpdate = existingFlow.get();
            entityToUpdate.setFlowDataJson(flowConfig.getFlowDataJson());
            entityToUpdate.setName(flowConfig.getName());
            return ResponseEntity.ok(flowConfigRepository.save(entityToUpdate));
        }

        // Se o tenantId não vier preenchido (cenário de teste), gera um UUID
        if (flowConfig.getTenantId() == null) {
            flowConfig.setTenantId(UUID.randomUUID());
        }

        return ResponseEntity.ok(flowConfigRepository.save(flowConfig));
    }

    /**
     * Endpoint para o Frontend carregar o fluxo quando a tela do painel abrir.
     * Devolve o JSON exato para o React Flow montar os bloquinhos visuais.
     */
    @GetMapping("/{tenantId}")
    public ResponseEntity<FlowConfigEntity> getActiveFlow(@PathVariable UUID tenantId) {
        return flowConfigRepository.findFirstByTenantIdAndActiveTrue(tenantId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}