package com.rival.chatbot.repository;

import com.rival.chatbot.domain.NlpIntentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NlpIntentRepository extends JpaRepository<NlpIntentEntity, UUID> {
    List<NlpIntentEntity> findByLanguage(String language);
}
