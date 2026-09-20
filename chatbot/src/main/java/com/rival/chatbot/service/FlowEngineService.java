package com.rival.chatbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rival.chatbot.domain.CustomerDataEntity;
import com.rival.chatbot.domain.FlowConfigEntity;
import com.rival.chatbot.dto.flow.FlowDefinition;
import com.rival.chatbot.repository.CustomerDataRepository;
import com.rival.chatbot.repository.FlowConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class FlowEngineService {

    private static final Logger log = LoggerFactory.getLogger(FlowEngineService.class);
    private final CustomerDataRepository customerDataRepository;
    private final FlowConfigRepository flowConfigRepository;
    private final ObjectMapper objectMapper;

    public FlowEngineService(CustomerDataRepository customerDataRepository,
                             FlowConfigRepository flowConfigRepository) {
        this.customerDataRepository = customerDataRepository;
        this.flowConfigRepository = flowConfigRepository;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Tenta processar a mensagem dentro do funil estruturado.
     * Retorna a resposta do próximo nó, ou NULL se o fluxo quebrou ou finalizou.
     */
    public String processFlow(UUID sessionId, UUID tenantId, String userMessage) {
        try {
            // 1. Busca o fluxo ativo da empresa
            Optional<FlowConfigEntity> activeFlowOpt = flowConfigRepository.findFirstByTenantIdAndActiveTrue(tenantId);
            if (activeFlowOpt.isEmpty()) return null;

            FlowDefinition flow = objectMapper.readValue(activeFlowOpt.get().getFlowDataJson(), FlowDefinition.class);

            // 2. Busca o estado atual do cliente ou CRIA NA HORA se não existir (evita falha com o @Async do Extractor)
            CustomerDataEntity customer = customerDataRepository.findBySessionId(sessionId)
                    .orElseGet(() -> {
                        CustomerDataEntity newCustomer = new CustomerDataEntity();
                        newCustomer.setSessionId(sessionId);
                        newCustomer.setTenantId(tenantId);
                        return customerDataRepository.save(newCustomer);
                    });

            String currentNodeId = customer.getCurrentNodeId();

            // Se for o primeiro contato, inicia no nó principal (ex: id "start")
            if (currentNodeId == null || currentNodeId.isBlank()) {
                currentNodeId = "start";
                customer.setCurrentNodeId(currentNodeId);
                customerDataRepository.save(customer);
                return getNodeText(flow, currentNodeId);
            }

            // 3. Procura qual linha (Edge) bate com a resposta do cliente
            String cleanMsg = userMessage.trim().toLowerCase();
            String nextNodeId = null;

            for (FlowDefinition.FlowEdge edge : flow.edges()) {
                if (edge.source().equals(currentNodeId)) {
                    // Verifica se a condição (ex: "1") bate com o que o usuário digitou
                    if (edge.condition() == null || edge.condition().equalsIgnoreCase(cleanMsg)) {
                        nextNodeId = edge.target();
                        break;
                    }
                }
            }

            // 4. Se o cliente respondeu errado, repete a pergunta atual
            if (nextNodeId == null) {
                return "Opção inválida. " + getNodeText(flow, currentNodeId);
            }

            // 5. Move o cliente para o novo Nó e salva no banco
            customer.setCurrentNodeId(nextNodeId);
            customerDataRepository.save(customer);

            return getNodeText(flow, nextNodeId);

        } catch (Exception e) {
            log.error("Erro ao processar máquina de estados do fluxo", e);
            return null;
        }
    }

    private String getNodeText(FlowDefinition flow, String nodeId) {
        return flow.nodes().stream()
                .filter(n -> n.id().equals(nodeId))
                .findFirst()
                .map(n -> n.data().text())
                .orElse("Fim do atendimento.");
    }
}