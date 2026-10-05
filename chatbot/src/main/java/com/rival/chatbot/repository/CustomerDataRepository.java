package com.rival.chatbot.repository;

import com.rival.chatbot.domain.CustomerDataEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerDataRepository extends JpaRepository<CustomerDataEntity, UUID> {
    Optional<CustomerDataEntity> findBySessionId(UUID sessionId);

    // NOVO: Query para filtrar clientes paginados pelo nome, telemóvel/ID ou canal
    @Query("SELECT c FROM CustomerDataEntity c WHERE " +
            "LOWER(COALESCE(c.name, '')) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(COALESCE(c.externalId, '')) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(COALESCE(c.channel, '')) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<CustomerDataEntity> searchCustomers(@Param("search") String search, Pageable pageable);
}