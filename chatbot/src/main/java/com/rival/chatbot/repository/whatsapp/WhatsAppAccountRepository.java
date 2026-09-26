package com.rival.chatbot.repository.whatsapp;

import com.rival.chatbot.domain.whatsapp.WhatsAppAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WhatsAppAccountRepository extends JpaRepository<WhatsAppAccountEntity, UUID> {
    Optional<WhatsAppAccountEntity> findByPhoneNumberId(String phoneNumberId);

    // Método atualizado para buscar pela instância da Evolution API
    Optional<WhatsAppAccountEntity> findByInstanceName(String instanceName);
}