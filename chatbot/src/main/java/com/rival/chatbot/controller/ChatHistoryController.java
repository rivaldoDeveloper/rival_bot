package com.rival.chatbot.controller;

import com.rival.chatbot.domain.ChatMessageEntity;
import com.rival.chatbot.domain.CustomerDataEntity;
import com.rival.chatbot.repository.ChatMessageRepository;
import com.rival.chatbot.repository.CustomerDataRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/chat/history")
@Tag(name = "Chat History", description = "Endpoints para consulta do historico de conversas")
public class ChatHistoryController {

    private final ChatMessageRepository chatMessageRepository;
    private final CustomerDataRepository customerDataRepository;

    public ChatHistoryController(ChatMessageRepository chatMessageRepository, CustomerDataRepository customerDataRepository) {
        this.chatMessageRepository = chatMessageRepository;
        this.customerDataRepository = customerDataRepository;
    }

    @Operation(summary = "Historico da Sessao", description = "Recupera todas as mensagens de uma sessao especifica com paginacao.")
    @GetMapping("/{sessionId}")
    @Transactional
    public ResponseEntity<Page<ChatMessageEntity>> getHistoryBySessionId(
            @PathVariable UUID sessionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        customerDataRepository.findBySessionId(sessionId).ifPresent(customer -> {
            if (customer.getUnreadCount() != null && customer.getUnreadCount() > 0) {
                customer.setUnreadCount(0);
                customerDataRepository.save(customer);
            }
        });
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<ChatMessageEntity> messages = chatMessageRepository.findBySessionId(sessionId, pageable);
        return ResponseEntity.ok(messages);
    }

    /* ========================================================================
     * MUDANÇA MULTI-TENANCY:
     * Recebe o X-Tenant-Id enviado pelo API Gateway / Interceptor e filtra
     * apenas as sessões do utilizador logado no Live Chat.
     * ======================================================================== */
    @Operation(summary = "Sessoes Ativas", description = "Lista todas as sessoes ativas filtradas por tenant.")
    @GetMapping("/sessions")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getActiveSessions(
            @RequestHeader(value = "X-Tenant-Id", required = false) UUID tenantId) {

        // Se o tenantId for enviado no header, filtra os clientes por tenant, caso contrário busca todos
        List<CustomerDataEntity> customers = (tenantId != null)
                ? customerDataRepository.findByTenantId(tenantId)
                : customerDataRepository.findAll();

        List<ChatMessageEntity> latestMessages = chatMessageRepository.findLatestMessagesPerSession();
        Map<UUID, String> lastMessageMap = latestMessages.stream()
                .collect(Collectors.toMap(
                        ChatMessageEntity::getSessionId,
                        ChatMessageEntity::getContent,
                        (existing, replacement) -> existing
                ));

        List<Map<String, Object>> sessions = new ArrayList<>();
        for (CustomerDataEntity customer : customers) {
            String lastMsgText = lastMessageMap.getOrDefault(customer.getSessionId(), "Sessão iniciada");
            Map<String, Object> dto = getStringObjectMap(customer, lastMsgText);
            dto.put("_sortDate", customer.getUpdatedAt());
            sessions.add(dto);
        }

        sessions.sort((s1, s2) -> {
            java.time.LocalDateTime date1 = (java.time.LocalDateTime) s1.get("_sortDate");
            java.time.LocalDateTime date2 = (java.time.LocalDateTime) s2.get("_sortDate");
            if (date1 == null && date2 == null) return 0;
            if (date1 == null) return 1;
            if (date2 == null) return -1;
            return date2.compareTo(date1);
        });

        sessions.forEach(s -> s.remove("_sortDate"));
        return ResponseEntity.ok(sessions);
    }

    private static Map<String, Object> getStringObjectMap(CustomerDataEntity customer, String lastMsgText) {
        Map<String, Object> dto = new HashMap<>(10);
        dto.put("sessionId", customer.getSessionId());
        dto.put("tenantId", customer.getTenantId());
        String displayName = customer.getName();

        if (displayName != null && displayName.contains("(")) {
            int start = displayName.indexOf("(");
            int end = displayName.indexOf(")");
            if (end > start) {
                displayName = displayName.substring(start + 1, end).trim();
            }
        }
        String channel = customer.getChannel() != null ? customer.getChannel() : "WEB";
        if (displayName == null || displayName.isBlank()) {
            if ("WEB".equalsIgnoreCase(channel)) {
                displayName = "Visitante Web " + customer.getSessionId().toString().substring(0, 4);
            } else {
                displayName = "Cliente " + customer.getSessionId().toString().substring(0, 5);
            }
        }
        dto.put("phoneNumber", displayName);
        dto.put("lastMessage", lastMsgText);
        dto.put("unread", customer.getUnreadCount() != null ? customer.getUnreadCount() : 0);
        dto.put("isAiActive", customer.getIsAiActive() != null ? customer.getIsAiActive() : true);
        dto.put("channel", channel);
        dto.put("customData", customer.getCustomData());
        return dto;
    }
}