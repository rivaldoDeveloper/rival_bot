package com.rival.chatbot.service.meta;

import com.rival.chatbot.domain.meta.MetaAccountEntity;
import java.util.List;
import java.util.UUID;

public interface MetaAccountService {
    MetaAccountEntity saveOrUpdateAccount(MetaAccountEntity account);
    List<MetaAccountEntity> getAllAccounts();
    void deleteAccount(UUID id);
}