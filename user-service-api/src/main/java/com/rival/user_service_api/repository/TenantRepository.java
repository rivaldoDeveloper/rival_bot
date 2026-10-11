package com.rival.user_service_api.repository;

import com.rival.user_service_api.entity.TenantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<TenantEntity, UUID> {

    // Método automático para procurar um Tenant pelo documento (CNPJ/NIF)
    Optional<TenantEntity> findByDocument(String document);
}