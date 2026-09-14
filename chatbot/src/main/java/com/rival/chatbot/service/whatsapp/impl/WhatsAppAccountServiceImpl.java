package com.rival.chatbot.service.whatsapp.impl;

import com.rival.chatbot.domain.whatsapp.WhatsAppAccountEntity;
import com.rival.chatbot.repository.whatsapp.WhatsAppAccountRepository;
import com.rival.chatbot.service.whatsapp.WhatsAppAccountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class WhatsAppAccountServiceImpl implements WhatsAppAccountService {

    private final WhatsAppAccountRepository repository;

    public WhatsAppAccountServiceImpl(WhatsAppAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public WhatsAppAccountEntity saveOrUpdateAccount(WhatsAppAccountEntity account) {
        return repository.findByPhoneNumberId(account.getPhoneNumberId())
                .map(existing -> {
                    existing.setApiToken(account.getApiToken());
                    existing.setDisplayPhoneNumber(account.getDisplayPhoneNumber());
                    if (account.getTenantId() != null) {
                        existing.setTenantId(account.getTenantId());
                    } else if (existing.getTenantId() == null) {
                        existing.setTenantId(UUID.randomUUID());
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
    public List<WhatsAppAccountEntity> getAllAccounts() {
        return repository.findAll();
    }
}