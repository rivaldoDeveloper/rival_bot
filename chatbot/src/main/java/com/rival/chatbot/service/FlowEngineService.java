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
    private final BotSettingsService botSettingsService;
    private final ObjectMapper objectMapper;

    public FlowEngineService(CustomerDataRepository customerDataRepository,
                             FlowConfigRepository flowConfigRepository,
                             DataExtractorService dataExtractorService,
                             BotSettingsService botSettingsService) {
        this.customerDataRepository = customerDataRepository;
        this.flowConfigRepository = flowConfigRepository;
        this.dataExtractorService = dataExtractorService;
        this.botSettingsService = botSettingsService;
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private boolean isStartOrResetKeyword(UUID tenantId, String message) {
        return botSettingsService.matchesResetKeyword(tenantId, message);
    }

    private boolean isExplicitRestartCommand(String message) {
        if (message == null || message.isBlank()) return false;
        String lower = message.toLowerCase().trim();
        return List.of("menu", "inicio", "início", "reiniciar", "recomecar", "recomeçar", "voltar", "start", "/start")
                .contains(lower);
    }

    /**
     * Verifica se a entrada é inválida/sem sentido (ex: "45464646466" ou apenas símbolos)
     * quando o fluxo espera uma resposta conversacional normal (e não uma extração de CPF/Telefone).
     */
    private boolean isInvalidConversationalInput(String message, boolean isExtractNode) {
        if (message == null || message.isBlank()) return true;
        String clean = message.trim();

        // Se for um bloco de Extração CRM (CPF, RG, Telefone), números são permitidos!
        if (isExtractNode) {
            return clean.length() < 2;
        }

        // Se o utilizador mandou apenas números soltos longos (ex: "45464646466") numa conversa normal
        if (clean.matches("^\\d{4,}$")) {
            return true;
        }

        // Se não tem nenhuma letra nem número válido
        if (!clean.matches(".*[\\p{L}0-9].*")) {
            return true;
        }

        // Ruído de teclado sem vogais (ex: "asdfghjkl", "bcdfgh")
        String onlyLetters = clean.replaceAll("[^\\p{L}]", "").toLowerCase();
        if (onlyLetters.length() >= 5 && !onlyLetters.matches(".*[aeiouáéíóúâêîôûãõàçy].*")) {
            return true;
        }

        return false;
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

            // 1. Reinício explícito ("menu", "voltar") ou nova saudação após o fim do fluxo ("NLP_MODE")
            if (isExplicitRestartCommand(userMessage) || ("NLP_MODE".equals(currentNodeId) && isStartOrResetKeyword(tenantId, userMessage))) {
                currentNodeId = null;
                customer.setCurrentNodeId(null);
            }

            // 2. Se ainda não iniciou o fluxo, verifica se a mensagem é um gatilho inicial ("oi", "olá")
            if (currentNodeId == null || currentNodeId.isBlank()) {
                if (!isStartOrResetKeyword(tenantId, userMessage)) {
                    log.info("Mensagem '{}' não é gatilho inicial de fluxo. Encaminhando para NLP/Fallback.", userMessage);
                    return null;
                }
            }

            // 3. Se já concluiu o fluxo visual, cede para a NLP
            if ("NLP_MODE".equals(currentNodeId)) {
                return null;
            }

            // 4. Verifica o nó onde o utilizador estava parado esperando resposta (question ou extract)
            FlowDefinitionDTO.FlowNode lastNode = (currentNodeId != null) ? getNodeById(flow, currentNodeId) : null;
            boolean isLastNodeExtract = lastNode != null && "extract".equalsIgnoreCase(lastNode.type());

            // Se estava no meio do fluxo e digitou algo inválido/sem sentido (ex: "45464646466"),
            // NÃO avança para o próximo bloco! Responde com o fallback configurado e mantém no nó atual.
            if (currentNodeId != null && isInvalidConversationalInput(userMessage, isLastNodeExtract)) {
                String fallbackMsg = botSettingsService.getSettingsByTenant(tenantId).getFallbackGenericMessage();
                return new ChatResponseDTO(
                        "TEXT:" + fallbackMsg,
                        null,
                        null,
                        "BOT",
                        false,
                        LocalDateTime.now()
                );
            }

            // 5. Se estava parado num bloco 'extract', salva a resposta do cliente no CRM
            if (isLastNodeExtract && lastNode.data() != null && lastNode.data().fieldToExtract() != null) {
                dataExtractorService.saveCustomField(sessionId, lastNode.data().fieldToExtract(), userMessage);
            }

            // 6. Define o ponto de partida
            String cursorNodeId = currentNodeId;
            if (cursorNodeId == null || cursorNodeId.isBlank()) {
                Optional<FlowDefinitionDTO.FlowNode> trigger = flow.nodes().stream()
                        .filter(n -> n.type() != null && n.type().toLowerCase().contains("trigger"))
                        .findFirst();
                if (trigger.isEmpty()) return null;
                cursorNodeId = trigger.get().id();
            }

            // 7. EXECUÇÃO EM CADEIA RESPEITANDO O TIPO DE CADA NODE:
            // - Nodes de envio ('text', 'image', 'audio', 'video') disparam e avançam automaticamente para o próximo!
            // - Nodes de espera ('question', 'extract') enviam a pergunta e PAUSAM o fluxo esperando a resposta do cliente!
            // - Node 'handoff' transfere para humano e encerra.
            List<String> sequence = new ArrayList<>();
            boolean requiresHandoff = false;
            Set<String> visitedInTurn = new HashSet<>();
            boolean pausedForUserInput = false;

            while (visitedInTurn.size() < 25) {
                FlowDefinitionDTO.FlowNode nextNode = findNextActiveNode(flow, cursorNodeId);

                if (nextNode == null) {
                    // Fim da linha: não há mais blocos conectados depois deste
                    customer.setCurrentNodeId("NLP_MODE");
                    break;
                }

                if (!visitedInTurn.add(nextNode.id())) {
                    break; // Evita loop infinito se o utilizador ligar uma seta para trás
                }

                String type = nextNode.type() != null ? nextNode.type().toLowerCase() : "";
                FlowDefinitionDTO.NodeData data = nextNode.data();

                if (type.contains("question") || type.contains("extract")) {
                    // BLOCO INTERATIVO: Envia a pergunta e PAUSA neste node esperando o utilizador responder!
                    if (data != null && data.text() != null && !data.text().trim().isEmpty()) {
                        sequence.add("TEXT:" + data.text().trim());
                    }
                    customer.setCurrentNodeId(nextNode.id());
                    pausedForUserInput = true;
                    break;
                } else if (type.contains("text") || type.contains("message")) {
                    // BLOCO DE MENSAGEM SIMPLES: Envia o texto e CONTINUA avançando para o próximo node!
                    if (data != null && data.text() != null && !data.text().trim().isEmpty()) {
                        sequence.add("TEXT:" + data.text().trim());
                    }
                    cursorNodeId = nextNode.id();
                } else if (type.contains("image")) {
                    if (data != null && data.imageUrl() != null && !data.imageUrl().trim().isEmpty()) {
                        sequence.add("IMAGE:" + data.imageUrl().trim());
                    }
                    cursorNodeId = nextNode.id();
                } else if (type.contains("audio")) {
                    if (data != null && data.audioUrl() != null && !data.audioUrl().trim().isEmpty()) {
                        sequence.add("AUDIO:" + data.audioUrl().trim());
                    }
                    cursorNodeId = nextNode.id();
                } else if (type.contains("video")) {
                    if (data != null && data.videoUrl() != null && !data.videoUrl().trim().isEmpty()) {
                        sequence.add("VIDEO:" + data.videoUrl().trim());
                    }
                    cursorNodeId = nextNode.id();
                } else if (type.contains("handoff")) {
                    requiresHandoff = true;
                    customer.setCurrentNodeId("NLP_MODE");
                    pausedForUserInput = true;
                    break;
                } else {
                    cursorNodeId = nextNode.id();
                }
            }

            if (!pausedForUserInput) {
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

    private FlowDefinitionDTO.FlowNode findNextActiveNode(FlowDefinitionDTO flow, String currentId) {
        String cursorId = currentId;
        Set<String> visited = new HashSet<>();

        while (visited.size() < 35) {
            if (!visited.add(cursorId)) break;

            String finalCursorId = cursorId;

            Optional<FlowDefinitionDTO.FlowEdge> edgeOpt = flow.edges().stream()
                    .filter(e -> e.sourceId() != null && e.sourceId().equals(finalCursorId))
                    .findFirst();

            if (edgeOpt.isEmpty()) return null;

            FlowDefinitionDTO.FlowNode targetNode = getNodeById(flow, edgeOpt.get().targetId());
            if (targetNode == null) return null;

            boolean isActive = targetNode.data() == null || targetNode.data().active() == null || targetNode.data().active();
            if (!isActive) {
                cursorId = targetNode.id();
                continue;
            }

            return targetNode;
        }
        return null;
    }
}