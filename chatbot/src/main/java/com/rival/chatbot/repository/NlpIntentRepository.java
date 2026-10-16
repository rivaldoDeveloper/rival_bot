package com.rival.chatbot.repository;

import com.rival.chatbot.domain.NlpIntentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NlpIntentRepository extends JpaRepository<NlpIntentEntity, UUID> {

    // Retorna a lista paginada de intenções filtrada exclusivamente pelo Tenant logado
    Page<NlpIntentEntity> findByTenantId(UUID tenantId, Pageable pageable);

    // Valida se a intenção já existe para o mesmo Tenant (evita conflitos de nomes entre empresas)
    List<NlpIntentEntity> findByTenantIdAndNameIgnoreCase(UUID tenantId, String name);

    // Retorna intenções por idioma restrito ao Tenant logado
    List<NlpIntentEntity> findByTenantIdAndLanguage(UUID tenantId, String language);

    // Métodos legados mantidos para compatibilidade do motor de busca
    List<NlpIntentEntity> findByLanguage(String language);

    List<NlpIntentEntity> findByNameIgnoreCase(String name);
}