package com.rival.chatbot.repository.meta;

import com.rival.chatbot.domain.meta.MetaAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MetaAccountRepository extends JpaRepository<MetaAccountEntity, UUID> {
    Optional<MetaAccountEntity> findByPageId(String pageId);
}