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
    private static final int DTO_INITIAL_CAPACITY = 10;

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
            Map<String, Object> dto = getStringObjectMap(customer, lastMsgText);

            sessions.add(dto);
        }

        return ResponseEntity.ok(sessions);
    }

    private static Map<String, Object> getStringObjectMap(CustomerDataEntity customer, String lastMsgText) {
        Map<String, Object> dto = new HashMap<>(DTO_INITIAL_CAPACITY);

        dto.put("sessionId", customer.getSessionId());
        //ADICIONE ESTA LINHA AQUI: Passa o tenantId real da base de dados para o Angular
        dto.put("tenantId", customer.getTenantId());
        dto.put("phoneNumber", customer.getName() != null ? customer.getName() : "Cliente " + customer.getSessionId().toString().substring(0, 5));
        dto.put("lastMessage", lastMsgText);
        dto.put("unread", customer.getUnreadCount() != null ? customer.getUnreadCount() : 0);
        dto.put("isAiActive", true);
        // Envia o canal de origem para o frontend exibir o ícone correto
        dto.put("channel", customer.getChannel() != null ? customer.getChannel() : "WHATSAPP");
        return dto;
    }
}