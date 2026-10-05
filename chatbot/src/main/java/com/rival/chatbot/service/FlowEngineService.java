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
    private final DataExtractorService dataExtractorService;
    private final ObjectMapper objectMapper;

    public FlowEngineService(CustomerDataRepository customerDataRepository,
                             FlowConfigRepository flowConfigRepository,
                             DataExtractorService dataExtractorService) {
        this.customerDataRepository = customerDataRepository;
        this.flowConfigRepository = flowConfigRepository;
        this.dataExtractorService = dataExtractorService;
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

            // Captura a resposta do cliente para o CRM se estava parado num bloco 'extract'
            if (currentNodeId != null && !currentNodeId.isBlank() && !"NLP_MODE".equals(currentNodeId)) {
                FlowDefinitionDTO.FlowNode lastNode = getNodeById(flow, currentNodeId);
                if (lastNode != null && "extract".equalsIgnoreCase(lastNode.type())) {
                    if (lastNode.data() != null && lastNode.data().fieldToExtract() != null) {
                        dataExtractorService.saveCustomField(sessionId, lastNode.data().fieldToExtract(), userMessage);
                    }
                }
            }

            // REINÍCIO INTELIGENTE: Se estava na IA mas disse "oi/menu", volta para o fluxo visual.
            if ("NLP_MODE".equals(currentNodeId)) {
                if (isResetKeyword(userMessage)) {
                    log.info("Reset Inteligente acionado. A repor o Flow Engine para a sessão {}", sessionId);
                    customer.setCurrentNodeId(null);
                    currentNodeId = null;
                } else {
                    return null;
                }
            }

            Queue<String> queue = new LinkedList<>();

            if (currentNodeId == null || currentNodeId.isBlank()) {
                Optional<FlowDefinitionDTO.FlowNode> trigger = flow.nodes().stream()
                        .filter(n -> n.type() != null && n.type().toLowerCase().contains("trigger"))
                        .findFirst();
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

            List<String> sequence = new ArrayList<>();
            String questionText = null;
            boolean requiresHandoff = false;
            boolean pausedAtQuestion = false;
            Set<String> visited = new HashSet<>();

            while (!queue.isEmpty() && visited.size() < 30) {
                String currId = queue.poll();
                if (!visited.add(currId)) continue;

                FlowDefinitionDTO.FlowNode node = getNodeById(flow, currId);
                if (node == null) continue;

                String type = node.type() != null ? node.type().toLowerCase() : "";
                FlowDefinitionDTO.NodeData data = node.data();

                if (type.contains("text") || type.contains("message")) {
                    if (data != null && data.text() != null && !data.text().trim().isEmpty()) {
                        sequence.add("TEXT:" + data.text().trim());
                    }
                } else if (type.contains("audio")) {
                    if (data != null && data.audioUrl() != null && !data.audioUrl().trim().isEmpty()) {
                        sequence.add("AUDIO:" + data.audioUrl().trim());
                    }
                } else if (type.contains("video")) {
                    if (data != null && data.videoUrl() != null && !data.videoUrl().trim().isEmpty()) {
                        sequence.add("VIDEO:" + data.videoUrl().trim());
                    }
                } else if (type.contains("question")) {
                    if (data != null && data.text() != null && !data.text().trim().isEmpty()) {
                        questionText = data.text().trim();
                        customer.setCurrentNodeId(currId);
                        pausedAtQuestion = true;
                        continue;
                    }
                } else if (type.contains("handoff")) {
                    requiresHandoff = true;
                    customer.setCurrentNodeId("NLP_MODE");
                    continue;
                } else if (type.contains("extract")) {
                    // Pausa o fluxo para pedir o dado ao cliente
                    if (data != null && data.text() != null && !data.text().trim().isEmpty()) {
                        questionText = data.text().trim();
                        customer.setCurrentNodeId(currId);
                        pausedAtQuestion = true;
                        continue;
                    }
                }

                List<String> nextIds = getNextActiveConnectedNodes(flow, currId);
                queue.addAll(nextIds);
            }

            if (questionText != null) {
                sequence.add("TEXT:" + questionText);
            }

            if (!pausedAtQuestion && !requiresHandoff) {
                customer.setCurrentNodeId("NLP_MODE");
            }

            customerDataRepository.save(customer);

            if (sequence.isEmpty() && !requiresHandoff) return null;

            String fullSequence = String.join("|||", sequence);

            return new ChatResponseDTO(
                    fullSequence,
                    null,
                    null,
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