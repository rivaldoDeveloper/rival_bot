package com.rival.chatbot.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rival.chatbot.domain.CustomerDataEntity;
import com.rival.chatbot.domain.FlowConfigEntity;
import com.rival.chatbot.dto.flow.FlowDefinitionDTO;
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
        // ✅ MAGIA AQUI: Diz ao Java para ignorar propriedades visuais (como o x e y) geradas pelo Angular!
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public String processFlow(UUID sessionId, UUID tenantId, String userMessage, String channel) {
        try {
            String currentChannel = (channel != null && !channel.isBlank()) ? channel : "WHATSAPP";
            Optional<FlowConfigEntity> activeFlowOpt = flowConfigRepository.findFirstByTenantIdAndChannelAndActiveTrue(
                    tenantId, currentChannel);

            if (activeFlowOpt.isEmpty()) {
                log.warn("Nenhum fluxo ativo encontrado no banco para o canal: {}", currentChannel);
                return null;
            }

            FlowDefinitionDTO flow = objectMapper.readValue(activeFlowOpt.get().getFlowDataJson(), FlowDefinitionDTO.class);

            CustomerDataEntity customer = customerDataRepository.findBySessionId(sessionId).orElseGet(() -> {
                CustomerDataEntity newCustomer = new CustomerDataEntity();
                newCustomer.setSessionId(sessionId);
                newCustomer.setTenantId(tenantId);
                newCustomer.setChannel(currentChannel);
                return customerDataRepository.save(newCustomer);
            });

            String currentNodeId = customer.getCurrentNodeId();

            if (currentNodeId == null || currentNodeId.isBlank()) {
                Optional<FlowDefinitionDTO.FlowNode> triggerNode = flow.nodes().stream()
                        .filter(n -> "trigger".equalsIgnoreCase(n.type()))
                        .findFirst();

                if (triggerNode.isEmpty()) {
                    log.warn("Nó 'trigger' inicial não encontrado no fluxo.");
                    return null;
                }

                String triggerId = triggerNode.get().id();
                String firstActionId = getNextConnectedNode(flow, triggerId);

                if (firstActionId == null) return null;

                customer.setCurrentNodeId(firstActionId);
                customerDataRepository.save(customer);
                return buildResponseAndCheckEnd(flow, firstActionId, customer);
            }

            String nextNodeId = getNextConnectedNode(flow, currentNodeId);

            if (nextNodeId == null) {
                customer.setCurrentNodeId(null);
                customerDataRepository.save(customer);
                return null;
            }

            customer.setCurrentNodeId(nextNodeId);
            customerDataRepository.save(customer);
            return buildResponseAndCheckEnd(flow, nextNodeId, customer);

        } catch (Exception e) {
            log.error("Erro CRÍTICO na leitura do Fluxo: {}", e.getMessage(), e);
            return null;
        }
    }

    private String getNextConnectedNode(FlowDefinitionDTO flow, String currentId) {
        for (FlowDefinitionDTO.FlowEdge edge : flow.edges()) {
            if (edge.sourceId() != null && edge.sourceId().equals(currentId)) {
                return edge.targetId();
            }
        }
        return null;
    }

    private String buildResponseAndCheckEnd(FlowDefinitionDTO flow, String nodeId, CustomerDataEntity customer) {
        Optional<FlowDefinitionDTO.FlowNode> nodeOpt = flow.nodes().stream().filter(n -> n.id().equals(nodeId)).findFirst();
        if (nodeOpt.isEmpty()) return null;

        FlowDefinitionDTO.FlowNode node = nodeOpt.get();

        boolean hasOutgoingEdges = flow.edges().stream().anyMatch(e -> e.sourceId() != null && e.sourceId().equals(nodeId));
        if (!hasOutgoingEdges) {
            customer.setCurrentNodeId(null);
            customerDataRepository.save(customer);
        }

        if ("audio".equalsIgnoreCase(node.type()) && node.data() != null && node.data().audioUrl() != null) {
            return "AUDIO:" + node.data().audioUrl();
        }
        return (node.data() != null && node.data().text() != null) ? node.data().text() : "Aguarde...";
    }
}