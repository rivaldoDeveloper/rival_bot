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
    public NlpIntentEntity createIntent(UUID tenantId, NlpIntentDTO dto) {
        // Fallback seguro de Tenant ID para evitar erros de restrição nula durante testes locais
        UUID activeTenantId = tenantId != null ? tenantId : UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d480");

        // Busca por nome no repositório restrito ao Tenant
        List<NlpIntentEntity> existingIntents = repository.findByTenantIdAndNameIgnoreCase(activeTenantId, dto.name());
        if (existingIntents != null && !existingIntents.isEmpty()) {
            throw new IllegalArgumentException("A intenção '" + dto.name() + "' já está cadastrada para sua conta.");
        }

        String cleanLang = dto.language();
        if (cleanLang != null) {
            cleanLang = cleanLang.split(" ")[0].split("-")[0].trim().toLowerCase();
            if (cleanLang.length() > 10) cleanLang = cleanLang.substring(0, 10);
        } else {
            cleanLang = "pt";
        }

        NlpIntentEntity entity = new NlpIntentEntity();
        entity.setTenantId(activeTenantId);
        entity.setName(dto.name().trim());
        entity.setLanguage(cleanLang);
        entity.setKeywords(dto.keywords() != null ? new ArrayList<>(dto.keywords()) : new ArrayList<>());
        entity.setResponses(dto.responses() != null ? new ArrayList<>(dto.responses()) : new ArrayList<>());

        NlpIntentEntity saved = repository.save(entity);
        localVectorNlpService.clearIntentsCache();
        return saved;
    }

    @Transactional
    public Optional<NlpIntentEntity> updateIntent(UUID tenantId, UUID id, NlpIntentDTO dto) {
        UUID activeTenantId = tenantId != null ? tenantId : UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d480");

        return repository.findById(id).map(entity -> {
            if (entity.getTenantId() != null && !entity.getTenantId().equals(activeTenantId)) {
                throw new IllegalArgumentException("Acesso negado: esta intenção pertence a outro Tenant.");
            }

            String cleanLang = dto.language();
            if (cleanLang != null) {
                cleanLang = cleanLang.split(" ")[0].split("-")[0].trim().toLowerCase();
                if (cleanLang.length() > 10) cleanLang = cleanLang.substring(0, 10);
            } else {
                cleanLang = "pt";
            }

            entity.setName(dto.name().trim());
            entity.setLanguage(cleanLang);
            entity.getKeywords().clear();
            if (dto.keywords() != null) entity.getKeywords().addAll(dto.keywords());

            entity.getResponses().clear();
            if (dto.responses() != null) entity.getResponses().addAll(dto.responses());

            NlpIntentEntity saved = repository.save(entity);
            localVectorNlpService.clearIntentsCache();
            return saved;
        });
    }

    @Transactional(readOnly = true)
    public Page<NlpIntentEntity> listIntents(UUID tenantId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        if (tenantId != null) {
            return repository.findByTenantId(tenantId, pageable);
        }
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
            localVectorNlpService.clearIntentsCache();
        });
    }
}