package com.rival.chatbot.service.meta.impl;

import com.rival.chatbot.domain.meta.MetaAccountEntity;
import com.rival.chatbot.repository.meta.MetaAccountRepository;
import com.rival.chatbot.service.meta.MetaAccountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class MetaAccountServiceImpl implements MetaAccountService {

    private final MetaAccountRepository repository;

    public MetaAccountServiceImpl(MetaAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public MetaAccountEntity saveOrUpdateAccount(MetaAccountEntity account) {
        return repository.findByPageId(account.getPageId())
                .map(existing -> {
                    existing.setPageAccessToken(account.getPageAccessToken());
                    existing.setVerifyToken(account.getVerifyToken());
                    existing.setPlatform(account.getPlatform());
                    if (account.getTenantId() != null) {
                        existing.setTenantId(account.getTenantId());
                    }
                    return repository.save(existing);
                })
                .orElseGet(() -> {
                    if (account.getTenantId() == null) {
                        account.setTenantId(UUID.randomUUID());
                    }
                    return repository.save(account);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<MetaAccountEntity> getAllAccounts() {
        return repository.findAll();
    }

    @Override
    @Transactional
    public void deleteAccount(UUID id) {
        repository.deleteById(id);
    }
}