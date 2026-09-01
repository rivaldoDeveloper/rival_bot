package com.rival.chatbot.repository;

import com.rival.chatbot.domain.CustomerDataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerDataRepository extends JpaRepository<CustomerDataEntity, UUID> {
    Optional<CustomerDataEntity> findBySessionId(UUID sessionId);
}