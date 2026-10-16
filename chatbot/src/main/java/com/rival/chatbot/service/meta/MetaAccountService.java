package com.rival.chatbot.service.meta;

import com.rival.chatbot.domain.meta.MetaAccountEntity;
import java.util.List;
import java.util.UUID;

public interface MetaAccountService {

    MetaAccountEntity saveOrUpdateAccount(MetaAccountEntity account);

    List<MetaAccountEntity> getAllAccounts();

    // MÉTODOS ADICIONADOS PARA ISOLAMENTO POR USUÁRIO/TENANT
    List<MetaAccountEntity> getAccountsByTenant(UUID tenantId);

    void deleteAccount(UUID id);
}