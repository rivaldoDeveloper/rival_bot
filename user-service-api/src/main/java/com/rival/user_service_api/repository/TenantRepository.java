package com.rival.user_service_api.repository;

import com.rival.user_service_api.entity.TenantEntity;
import com.rival.user_service_api.enums.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<TenantEntity, UUID> {

    // Método automático para procurar um Tenant pelo documento (CNPJ/NIF)
    Optional<TenantEntity> findByDocument(String document);

    // Agora retorna uma Página de resultados, dependendo do limite que pedirmos
    Page<TenantEntity> findByDueDateBeforeAndSubscriptionStatusIn(LocalDate dataAtual, List<SubscriptionStatus> status, Pageable pageable);
}