package com.rival.chatbot.repository;

import com.rival.chatbot.domain.NlpIntentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NlpIntentRepository extends JpaRepository<NlpIntentEntity, UUID> {

    // Retorna a lista paginada de intenções filtrada exclusivamente pelo Tenant logado
    Page<NlpIntentEntity> findByTenantId(UUID tenantId, Pageable pageable);

    // Retorna todas as intenções do Tenant para validação rápida de conflitos e importação em lote
    List<NlpIntentEntity> findAllByTenantId(UUID tenantId);

    // Pesquisa por ID da Intenção (name), Gatilhos (keywords) ou Respostas (responses) isolada por Tenant
    @Query("SELECT i FROM NlpIntentEntity i WHERE i.tenantId = :tenantId AND " +
            "(:search IS NULL OR :search = '' OR " +
            "LOWER(i.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "EXISTS (SELECT 1 FROM i.keywords k WHERE LOWER(k) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
            "EXISTS (SELECT 1 FROM i.responses r WHERE LOWER(r) LIKE LOWER(CONCAT('%', :search, '%'))))")
    Page<NlpIntentEntity> searchIntentsByTenant(@Param("tenantId") UUID tenantId, @Param("search") String search, Pageable pageable);

    // Pesquisa global (fallback caso tenantId seja nulo)
    @Query("SELECT i FROM NlpIntentEntity i WHERE " +
            "(:search IS NULL OR :search = '' OR " +
            "LOWER(i.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "EXISTS (SELECT 1 FROM i.keywords k WHERE LOWER(k) LIKE LOWER(CONCAT('%', :search, '%'))) OR " +
            "EXISTS (SELECT 1 FROM i.responses r WHERE LOWER(r) LIKE LOWER(CONCAT('%', :search, '%'))))")
    Page<NlpIntentEntity> searchIntents(@Param("search") String search, Pageable pageable);

    // Valida se a intenção já existe para o mesmo Tenant (evita conflitos de nomes entre empresas)
    List<NlpIntentEntity> findByTenantIdAndNameIgnoreCase(UUID tenantId, String name);

    // Retorna intenções por idioma restrito ao Tenant logado
    List<NlpIntentEntity> findByTenantIdAndLanguage(UUID tenantId, String language);

    // Métodos legados mantidos para compatibilidade do motor de busca
    List<NlpIntentEntity> findByLanguage(String language);
    List<NlpIntentEntity> findByNameIgnoreCase(String name);
}