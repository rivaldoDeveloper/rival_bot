package com.rival.chatbot.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rival.chatbot.domain.CustomerDataEntity;
import com.rival.chatbot.domain.FlowConfigEntity;
import com.rival.chatbot.dto.ChatResponseDTO;
import com.rival.chatbot.dto.flow.FlowDefinitionDTO;
import com.rival.chatbot.repository.CustomerDataRepository;
import com.rival.chatbot.repository.FlowConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

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
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    // Identifica palavras comuns que devem reiniciar o fluxo visual automaticamente
    private boolean isResetKeyword(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase().trim();
        return lower.matches("^(oi|olá|ola|menu|inicio|início|começar|start|voltar).*");
    }

    public ChatResponseDTO processFlow(UUID sessionId, UUID tenantId, String userMessage, String channel) {
        try {
            String currentChannel = (channel != null && !channel.isBlank()) ? channel : "WHATSAPP";
            Optional<FlowConfigEntity> activeFlowOpt = flowConfigRepository.findFirstByTenantIdAndChannelAndActiveTrue(tenantId, currentChannel);

            if (activeFlowOpt.isEmpty()) return null;

            FlowDefinitionDTO flow = objectMapper.readValue(activeFlowOpt.get().getFlowDataJson(), FlowDefinitionDTO.class);
            CustomerDataEntity customer = customerDataRepository.findBySessionId(sessionId).orElseGet(() -> {
                CustomerDataEntity newCustomer = new CustomerDataEntity();
                newCustomer.setSessionId(sessionId);
                newCustomer.setTenantId(tenantId);
                newCustomer.setChannel(currentChannel);
                return customerDataRepository.save(newCustomer);
            });

            String currentNodeId = customer.getCurrentNodeId();

            // REINÍCIO INTELIGENTE: Se estava na IA mas disse "oi/menu", volta para o fluxo visual.
            if ("NLP_MODE".equals(currentNodeId)) {
                if (isResetKeyword(userMessage)) {
                    log.info("Palavra-chave de reinício detetada ({}). A repor o Flow Engine para a sessão {}", userMessage, sessionId);
                    customer.setCurrentNodeId(null);
                    currentNodeId = null;
                } else {
                    return null; // Continua a falar com a IA Generativa/NLP
                }
            }

            Queue<String> queue = new LinkedList<>();

            if (currentNodeId == null || currentNodeId.isBlank()) {
                Optional<FlowDefinitionDTO.FlowNode> trigger = flow.nodes().stream().filter(n -> "trigger".equalsIgnoreCase(n.type())).findFirst();
                if (trigger.isEmpty()) return null;
                queue.addAll(getNextActiveConnectedNodes(flow, trigger.get().id()));
            } else {
                queue.addAll(getNextActiveConnectedNodes(flow, currentNodeId));
            }

            if (queue.isEmpty()) {
                customer.setCurrentNodeId("NLP_MODE");
                customerDataRepository.save(customer);
                return null;
            }

            StringBuilder combinedText = new StringBuilder();
            String outAudio = null;
            String outVideo = null;
            boolean requiresHandoff = false;
            boolean pausedAtQuestion = false;
            Set<String> visited = new HashSet<>();

            // Agrega os nós sequenciais num único envio (Cascata Contínua)
            while (!queue.isEmpty() && visited.size() < 20) {
                String currId = queue.poll();
                if (!visited.add(currId)) continue;

                FlowDefinitionDTO.FlowNode node = getNodeById(flow, currId);
                if (node == null) continue;

                String type = node.type();
                FlowDefinitionDTO.NodeData data = node.data();

                if ("text".equalsIgnoreCase(type) && data != null && data.text() != null) {
                    if (!combinedText.isEmpty()) combinedText.append("\n\n");
                    combinedText.append(data.text());
                } else if ("audio".equalsIgnoreCase(type) && data != null && data.audioUrl() != null) {
                    outAudio = data.audioUrl();
                } else if ("video".equalsIgnoreCase(type) && data != null && data.videoUrl() != null) {
                    outVideo = data.videoUrl();
                } else if ("question".equalsIgnoreCase(type) && data != null && data.text() != null) {
                    if (!combinedText.isEmpty()) combinedText.append("\n\n");
                    combinedText.append(data.text());

                    customer.setCurrentNodeId(currId);
                    customerDataRepository.save(customer);
                    pausedAtQuestion = true;
                    break;
                } else if ("handoff".equalsIgnoreCase(type)) {
                    requiresHandoff = true;
                    customer.setCurrentNodeId("NLP_MODE");
                    customerDataRepository.save(customer);
                    break;
                }

                List<String> nextIds = getNextActiveConnectedNodes(flow, currId);
                if (nextIds.isEmpty()) {
                    if (!pausedAtQuestion) customer.setCurrentNodeId("NLP_MODE");
                } else {
                    queue.addAll(nextIds);
                }
            }

            if (!pausedAtQuestion && !requiresHandoff) {
                customer.setCurrentNodeId("NLP_MODE");
                customerDataRepository.save(customer);
            }

            if (combinedText.isEmpty() && outAudio == null && outVideo == null && !requiresHandoff) return null;

            return new ChatResponseDTO(
                    combinedText.toString().trim(),
                    outVideo,
                    outAudio,
                    "BOT",
                    requiresHandoff,
                    LocalDateTime.now()
            );

        } catch (Exception e) {
            log.error("Erro na leitura estruturada do Fluxo.", e);
            return null;
        }
    }

    private FlowDefinitionDTO.FlowNode getNodeById(FlowDefinitionDTO flow, String id) {
        return flow.nodes().stream().filter(n -> n.id().equals(id)).findFirst().orElse(null);
    }

    private List<String> getNextActiveConnectedNodes(FlowDefinitionDTO flow, String currentId) {
        return flow.edges().stream()
                .filter(e -> e.sourceId() != null && e.sourceId().equals(currentId))
                .map(FlowDefinitionDTO.FlowEdge::targetId)
                .filter(targetId -> {
                    FlowDefinitionDTO.FlowNode n = getNodeById(flow, targetId);
                    if (n == null) return false;
                    return n.data() == null || n.data().active() == null || n.data().active();
                })
                .toList();
    }
}