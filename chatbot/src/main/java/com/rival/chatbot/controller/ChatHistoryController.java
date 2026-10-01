package com.rival.chatbot.controller;

import com.rival.chatbot.domain.ChatMessageEntity;
import com.rival.chatbot.domain.CustomerDataEntity;
import com.rival.chatbot.repository.ChatMessageRepository;
import com.rival.chatbot.repository.CustomerDataRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/chat/history")
public class ChatHistoryController {

    private final ChatMessageRepository chatMessageRepository;
    private final CustomerDataRepository customerDataRepository;

    public ChatHistoryController(ChatMessageRepository chatMessageRepository, CustomerDataRepository customerDataRepository) {
        this.chatMessageRepository = chatMessageRepository;
        this.customerDataRepository = customerDataRepository;
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<Page<ChatMessageEntity>> getHistoryBySessionId(
            @PathVariable UUID sessionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        customerDataRepository.findBySessionId(sessionId).ifPresent(customer -> {
            customer.setUnreadCount(0);
            customerDataRepository.save(customer);
        });

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<ChatMessageEntity> messages = chatMessageRepository.findBySessionId(sessionId, pageable);
        return ResponseEntity.ok(messages);
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<Map<String, Object>>> getActiveSessions() {
        List<CustomerDataEntity> customers = customerDataRepository.findAll();
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

            // Pasamos el customer completo para poder usar updatedAt en la ordenación
            Map<String, Object> dto = getStringObjectMap(customer, lastMsgText);

            // Añadimos temporalmente la fecha de actualización para usarla en el sort
            dto.put("_sortDate", customer.getUpdatedAt());

            sessions.add(dto);
        }

        // ORDENAMIENTO: Ordena la lista de forma descendente (los más recientes primero)
        sessions.sort((s1, s2) -> {
            java.time.LocalDateTime date1 = (java.time.LocalDateTime) s1.get("_sortDate");
            java.time.LocalDateTime date2 = (java.time.LocalDateTime) s2.get("_sortDate");
            if (date1 == null && date2 == null) return 0;
            if (date1 == null) return 1;
            if (date2 == null) return -1;
            return date2.compareTo(date1);
        });

        // Limpieza de la variable temporal usada para ordenar para no ensuciar el JSON de salida
        sessions.forEach(s -> s.remove("_sortDate"));

        return ResponseEntity.ok(sessions);
    }

    private static Map<String, Object> getStringObjectMap(CustomerDataEntity customer, String lastMsgText) {
        Map<String, Object> dto = new HashMap<>(10);
        dto.put("sessionId", customer.getSessionId());
        dto.put("tenantId", customer.getTenantId());

        String displayName = customer.getName();

        // ✅ CORREÇÃO: Agora apagamos o nome de fora e mantemos apenas o que está dentro dos parênteses (o Bot)
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

        return dto;
    }
}