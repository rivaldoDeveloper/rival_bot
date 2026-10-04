package com.rival.chatbot.controller;

import com.rival.chatbot.domain.FlowConfigEntity;
import com.rival.chatbot.repository.FlowConfigRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
        // Requer criar o método List<FlowConfigEntity> findAllByTenantIdAndChannel(...) no Repository
        List<FlowConfigEntity> existingFlows = flowConfigRepository.findAllByTenantIdAndChannel(flowConfig.getTenantId(), flowConfig.getChannel());

        if (!existingFlows.isEmpty()) {
            // Atualiza a primeira linha encontrada
            FlowConfigEntity entityToUpdate = existingFlows.get(0);
            entityToUpdate.setFlowDataJson(flowConfig.getFlowDataJson());
            entityToUpdate.setName(flowConfig.getName());
            entityToUpdate.setActive(flowConfig.isActive()); // Garante a atualização da Flag Principal

            // Deleta as duplicatas fantasmas para evitar o loop
            if (existingFlows.size() > 1) {
                for (int i = 1; i < existingFlows.size(); i++) {
                    flowConfigRepository.delete(existingFlows.get(i));
                }
            }
            return ResponseEntity.ok(flowConfigRepository.save(entityToUpdate));
        }

        if (flowConfig.getTenantId() == null) {
            flowConfig.setTenantId(UUID.randomUUID());
        }
        return ResponseEntity.ok(flowConfigRepository.save(flowConfig));
    }

    /**
     * Endpoint para o Frontend carregar o fluxo quando a tela do painel abrir.
     * Devolve o JSON exato para o React Flow montar os bloquinhos visuais.
     */
    @GetMapping("/{tenantId}/{channel}")
    public ResponseEntity<FlowConfigEntity> getActiveFlow(@PathVariable UUID tenantId, @PathVariable String channel) {
        return flowConfigRepository.findFirstByTenantIdAndChannel(tenantId, channel)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}