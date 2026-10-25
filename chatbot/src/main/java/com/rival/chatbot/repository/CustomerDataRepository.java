package com.rival.chatbot.repository;

import com.rival.chatbot.domain.CustomerDataEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerDataRepository extends JpaRepository<CustomerDataEntity, UUID> {

    Optional<CustomerDataEntity> findBySessionId(UUID sessionId);

    Optional<CustomerDataEntity> findBySessionIdAndTenantId(UUID sessionId, UUID tenantId);

    /* ========================================================================
     * MUDANÇA MULTI-TENANCY:
     * Busca todas as conversas/clientes ativos pertencentes a um único Tenant.
     * ======================================================================== */
    List<CustomerDataEntity> findByTenantId(UUID tenantId);

    @Query("SELECT c FROM CustomerDataEntity c WHERE " +
            "SIZE(c.customData) > 0 AND " +
            "(c.externalId IS NULL OR LOWER(c.externalId) NOT LIKE 'sim_%') AND " +
            "(:search IS NULL OR :search = '' OR " +
            "LOWER(COALESCE(c.name, '')) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(COALESCE(c.externalId, '')) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(COALESCE(c.channel, '')) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<CustomerDataEntity> searchCustomers(@Param("search") String search, Pageable pageable);

    @Query("SELECT c FROM CustomerDataEntity c WHERE c.tenantId = :tenantId AND " +
            "SIZE(c.customData) > 0 AND " +
            "(c.externalId IS NULL OR LOWER(c.externalId) NOT LIKE 'sim_%') AND " +
            "(:search IS NULL OR :search = '' OR " +
            "LOWER(COALESCE(c.name, '')) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(COALESCE(c.externalId, '')) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(COALESCE(c.channel, '')) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<CustomerDataEntity> searchCustomersByTenant(@Param("tenantId") UUID tenantId, @Param("search") String search, Pageable pageable);
}