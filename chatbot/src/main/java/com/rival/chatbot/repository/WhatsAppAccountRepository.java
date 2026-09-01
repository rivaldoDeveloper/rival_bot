package com.rival.chatbot.repository;

import com.rival.chatbot.domain.WhatsAppAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WhatsAppAccountRepository extends JpaRepository<WhatsAppAccountEntity, UUID> {
    Optional<WhatsAppAccountEntity> findByPhoneNumberId(String phoneNumberId);
}