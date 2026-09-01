package com.rival.chatbot.service.impl;

import com.rival.chatbot.domain.ChatMessageEntity;
import com.rival.chatbot.dto.ChatRequestDTO;
import com.rival.chatbot.dto.ChatResponseDTO;
import com.rival.chatbot.mapper.ChatMapper;
import com.rival.chatbot.repository.ChatMessageRepository;
import com.rival.chatbot.service.ChatService;
import com.rival.chatbot.service.DataExtractorService;
import com.rival.chatbot.service.NlpEngineService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ChatServiceImpl implements ChatService {

    private final ChatMessageRepository repository;
    private final ChatMapper chatMapper;
    private final NlpEngineService nlpEngineService;
    private final DataExtractorService dataExtractorService;

    public ChatServiceImpl(ChatMessageRepository repository,
                           ChatMapper chatMapper,
                           NlpEngineService nlpEngineService,
                           DataExtractorService dataExtractorService) {
        this.repository = repository;
        this.chatMapper = chatMapper;
        this.nlpEngineService = nlpEngineService;
        this.dataExtractorService = dataExtractorService;
    }

    @Override
    @Transactional
    public ChatResponseDTO processMessage(ChatRequestDTO request) {
        // 1. Salva a mensagem recebida no histórico do chat
        ChatMessageEntity userEntity = chatMapper.toEntity(request);
        repository.save(userEntity);

        // 2. EXTRAÇÃO AUTOMÁTICA DE DADOS (Salva na tabela 'customer_data')
        dataExtractorService.extractAndSave(request.sessionId(), request.tenantId(), request.message());

        // 3. Regra de Handoff (Atendente Humano)
        String userMsg = request.message().toLowerCase();
        if (userMsg.contains("atendente") || userMsg.contains("humano") || userMsg.contains("suporte")) {
            return new ChatResponseDTO(
                    "Entendido! Estou transferindo o seu atendimento para um operador humano.",
                    "BOT",
                    true,
                    LocalDateTime.now()
            );
        }

        // 4. Processamento NLP
        String nlpResponse = nlpEngineService.processAndMatch(request.message());

        // 5. Salva resposta do BOT no banco
        ChatMessageEntity botEntity = new ChatMessageEntity();
        botEntity.setSessionId(request.sessionId());
        botEntity.setTenantId(request.tenantId());
        botEntity.setContent(nlpResponse);
        botEntity.setSenderType("BOT");
        repository.save(botEntity);

        return new ChatResponseDTO(nlpResponse, "BOT", false, LocalDateTime.now());
    }
}