package com.rival.chatbot.service;

import com.rival.chatbot.domain.NlpIntentEntity;
import com.rival.chatbot.dto.NlpIntentDTO;
import com.rival.chatbot.repository.NlpIntentRepository;
import com.rival.chatbot.util.TextNormalizerUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class NlpTrainingService {

    private final NlpIntentRepository repository;
    private final LocalVectorNlpService localVectorNlpService;

    public NlpTrainingService(NlpIntentRepository repository, LocalVectorNlpService localVectorNlpService) {
        this.repository = repository;
        this.localVectorNlpService = localVectorNlpService;
    }

    private List<String> deduplicateKeywordsWithinSameIntent(List<String> rawKeywords) {
        if (rawKeywords == null) return new ArrayList<>();
        Map<String, String> uniqueMap = new LinkedHashMap<>();
        for (String kw : rawKeywords) {
            if (kw == null || kw.isBlank()) continue;
            String cleanKey = TextNormalizerUtil.cleanAndDeduplicate(kw);
            if (!cleanKey.isBlank() && !uniqueMap.containsKey(cleanKey)) {
                String trimmed = kw.trim().toLowerCase();
                if (trimmed.length() > 250) {
                    trimmed = trimmed.substring(0, 250);
                }
                uniqueMap.put(cleanKey, trimmed);
            }
        }
        return new ArrayList<>(uniqueMap.values());
    }

    private List<String> deduplicateResponsesWithinSameIntent(List<String> rawResponses) {
        if (rawResponses == null) return new ArrayList<>();
        Map<String, String> uniqueMap = new LinkedHashMap<>();
        for (String resp : rawResponses) {
            if (resp == null || resp.isBlank()) continue;
            String key = resp.trim().toLowerCase();
            if (!uniqueMap.containsKey(key)) {
                uniqueMap.put(key, resp.trim());
            }
        }
        return new ArrayList<>(uniqueMap.values());
    }

    private String sanitizeLanguage(String rawLang) {
        if (rawLang != null && !rawLang.isBlank()) {
            String clean = rawLang.split(" ")[0].split("-")[0].trim().toLowerCase();
            return clean.length() > 10 ? clean.substring(0, 10) : clean;
        }
        return "pt";
    }

    @Transactional
    public NlpIntentEntity createIntent(UUID tenantId, NlpIntentDTO dto) {
        // Fallback seguro de Tenant ID para evitar erros de restrição nula durante testes locais
        if (tenantId == null) {
            throw new IllegalArgumentException("O identificador do Tenant (X-Tenant-Id) é obrigatório.");
        }

        List<NlpIntentEntity> existingIntents = repository.findByTenantIdAndNameIgnoreCase(tenantId, dto.name().trim());
        if (existingIntents != null && !existingIntents.isEmpty()) {
            throw new IllegalArgumentException("A intenção '" + dto.name() + "' já está cadastrada para sua conta.");
        }

        List<String> cleanKeywords = deduplicateKeywordsWithinSameIntent(dto.keywords());
        List<String> cleanResponses = deduplicateResponsesWithinSameIntent(dto.responses());

        if (cleanKeywords.isEmpty()) {
            throw new IllegalArgumentException("Informe pelo menos um gatilho válido.");
        }

        NlpIntentEntity entity = new NlpIntentEntity();
        entity.setTenantId(tenantId);
        entity.setName(dto.name().trim());
        entity.setLanguage(sanitizeLanguage(dto.language()));
        entity.setKeywords(cleanKeywords);
        entity.setResponses(cleanResponses);

        NlpIntentEntity saved = repository.save(entity);
        localVectorNlpService.clearIntentsCache();
        return saved;
    }

    @Transactional
    public Optional<NlpIntentEntity> updateIntent(UUID tenantId, UUID id, NlpIntentDTO dto) {
        if (tenantId == null) {
            throw new IllegalArgumentException("O identificador do Tenant (X-Tenant-Id) é obrigatório.");
        }

        List<String> cleanKeywords = deduplicateKeywordsWithinSameIntent(dto.keywords());
        List<String> cleanResponses = deduplicateResponsesWithinSameIntent(dto.responses());

        return repository.findById(id).map(entity -> {
            if (entity.getTenantId() != null && !entity.getTenantId().equals(tenantId)) {
                throw new IllegalArgumentException("Acesso negado: esta intenção pertence a outro Tenant.");
            }
            entity.setName(dto.name().trim());
            entity.setLanguage(sanitizeLanguage(dto.language()));
            entity.setKeywords(new ArrayList<>(cleanKeywords));
            entity.setResponses(new ArrayList<>(cleanResponses));

            NlpIntentEntity saved = repository.save(entity);
            localVectorNlpService.clearIntentsCache();
            return saved;
        });
    }

    @Transactional
    public Map<String, Object> importBulkIntents(UUID tenantId, List<NlpIntentDTO> dtos) {
        if (tenantId == null) {
            throw new IllegalArgumentException("O identificador do Tenant (X-Tenant-Id) é obrigatório.");
        }
        if (dtos == null || dtos.isEmpty()) {
            throw new IllegalArgumentException("O JSON enviado não contém intenções válidas.");
        }

        List<NlpIntentEntity> existingList = repository.findAllByTenantId(tenantId);
        Map<String, NlpIntentEntity> existingByName = new LinkedHashMap<>();
        for (NlpIntentEntity ext : existingList) {
            if (ext.getName() != null) {
                existingByName.put(ext.getName().trim().toLowerCase(), ext);
            }
        }

        int createdCount = 0;
        int updatedCount = 0;
        Map<String, NlpIntentEntity> entitiesToSaveByName = new LinkedHashMap<>();

        for (NlpIntentDTO dto : dtos) {
            if (dto == null || dto.name() == null || dto.name().isBlank()) continue;

            String cleanNameKey = dto.name().trim().toLowerCase();
            NlpIntentEntity targetEntity = existingByName.get(cleanNameKey);

            List<String> incomingKeywords = deduplicateKeywordsWithinSameIntent(dto.keywords());
            List<String> incomingResponses = deduplicateResponsesWithinSameIntent(dto.responses());

            if (incomingKeywords.isEmpty() || incomingResponses.isEmpty()) continue;

            if (targetEntity != null) {
                List<String> mergedKeywords = new ArrayList<>(targetEntity.getKeywords() != null ? targetEntity.getKeywords() : List.of());
                mergedKeywords.addAll(incomingKeywords);
                targetEntity.setKeywords(deduplicateKeywordsWithinSameIntent(mergedKeywords));

                List<String> mergedResponses = new ArrayList<>(targetEntity.getResponses() != null ? targetEntity.getResponses() : List.of());
                mergedResponses.addAll(incomingResponses);
                targetEntity.setResponses(deduplicateResponsesWithinSameIntent(mergedResponses));

                targetEntity.setLanguage(sanitizeLanguage(dto.language()));

                if (!entitiesToSaveByName.containsKey(cleanNameKey)) {
                    updatedCount++;
                }
                entitiesToSaveByName.put(cleanNameKey, targetEntity);
            } else {
                NlpIntentEntity newEntity = new NlpIntentEntity();
                newEntity.setTenantId(tenantId);
                newEntity.setName(dto.name().trim());
                newEntity.setLanguage(sanitizeLanguage(dto.language()));
                newEntity.setKeywords(new ArrayList<>(incomingKeywords));
                newEntity.setResponses(new ArrayList<>(incomingResponses));

                existingByName.put(cleanNameKey, newEntity);
                entitiesToSaveByName.put(cleanNameKey, newEntity);
                createdCount++;
            }
        }

        if (!entitiesToSaveByName.isEmpty()) {
            repository.saveAllAndFlush(entitiesToSaveByName.values());
            localVectorNlpService.clearIntentsCache();
        }

        return Map.of(
                "created", createdCount,
                "updated", updatedCount,
                "totalProcessed", createdCount + updatedCount
        );
    }

    @Transactional(readOnly = true)
    public Page<NlpIntentEntity> listIntents(UUID tenantId, String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        String cleanSearch = (search != null) ? search.trim() : "";

        if (tenantId != null) {
            return repository.searchIntentsByTenant(tenantId, cleanSearch, pageable);
        }
        return repository.searchIntents(cleanSearch, pageable);
    }

    @Transactional
    public void deleteIntent(UUID id) {
        repository.findById(id).ifPresent(intent -> {
            // 1. Limpa as listas filhas para forçar o Hibernate a deletar as relações no banco primeiro
            intent.getKeywords().clear();
            intent.getResponses().clear();
            repository.saveAndFlush(intent);

            // 2. Agora deleta a intenção pai com segurança
            repository.delete(intent);
            localVectorNlpService.clearIntentsCache();
        });
    }
}