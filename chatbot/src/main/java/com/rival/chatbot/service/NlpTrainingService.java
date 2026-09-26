package com.rival.chatbot.service;

import com.rival.chatbot.domain.NlpIntentEntity;
import com.rival.chatbot.dto.NlpIntentDTO;
import com.rival.chatbot.repository.NlpIntentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class NlpTrainingService {

    private final NlpIntentRepository repository;
    private final LocalVectorNlpService localVectorNlpService;

    public NlpTrainingService(NlpIntentRepository repository, LocalVectorNlpService localVectorNlpService) {
        this.repository = repository;
        this.localVectorNlpService = localVectorNlpService;
    }

    @Transactional
    public NlpIntentEntity createIntent(NlpIntentDTO dto) {
        // Busca por nome no repositório
        List<NlpIntentEntity> existingIntents = repository.findByNameIgnoreCase(dto.name());

        // Se a intenção já existir, lança uma exceção para o Controller tratar
        if (existingIntents != null && !existingIntents.isEmpty()) {
            throw new IllegalArgumentException("A intenção '" + dto.name() + "' já está cadastrada. Não é permitido salvar intenções duplicadas.");
        }

        NlpIntentEntity entity = new NlpIntentEntity();
        entity.setName(dto.name());
        entity.setLanguage(dto.language());
        entity.setKeywords(dto.keywords() != null ? new ArrayList<>(dto.keywords()) : new ArrayList<>());
        entity.setResponses(dto.responses() != null ? new ArrayList<>(dto.responses()) : new ArrayList<>());

        NlpIntentEntity saved = repository.save(entity);
        localVectorNlpService.clearIntentsCache(); // Invalida o Cache

        return saved;
    }

    @Transactional
    public Optional<NlpIntentEntity> updateIntent(UUID id, NlpIntentDTO dto) {
        return repository.findById(id).map(entity -> {
            entity.setName(dto.name());
            entity.setLanguage(dto.language());

            // Limpa as listas antigas e adiciona as novas
            entity.getKeywords().clear();
            if (dto.keywords() != null) entity.getKeywords().addAll(dto.keywords());

            entity.getResponses().clear();
            if (dto.responses() != null) entity.getResponses().addAll(dto.responses());

            NlpIntentEntity saved = repository.save(entity);
            localVectorNlpService.clearIntentsCache(); // Invalida o cache
            return saved;
        });
    }

    @Transactional(readOnly = true)
    public Page<NlpIntentEntity> listIntents(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        return repository.findAll(pageable);
    }

    @Transactional
    public void deleteIntent(UUID id) {
        repository.findById(id).ifPresent(intent -> {
            // 1. Limpa as listas filhas para forçar o Hibernate a deletar as relações no banco primeiro
            intent.getKeywords().clear();
            intent.getResponses().clear();
            repository.saveAndFlush(intent); // Força a sincronização imediata

            // 2. Agora deleta a intenção pai com segurança
            repository.delete(intent);

            // 3. Limpa o cache do robô
            localVectorNlpService.clearIntentsCache();
        });
    }
}