package com.rival.chatbot.controller.whatsapp;

import com.rival.chatbot.domain.whatsapp.WhatsAppAccountEntity;
import com.rival.chatbot.repository.whatsapp.WhatsAppAccountRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/whatsapp/accounts")
public class WhatsAppAccountController {

    private final WhatsAppAccountRepository repository;

    public WhatsAppAccountController(WhatsAppAccountRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<WhatsAppAccountEntity> registerOrUpdateAccount(@Valid @RequestBody WhatsAppAccountEntity account) {
        WhatsAppAccountEntity entityToSave = repository.findByPhoneNumberId(account.getPhoneNumberId())
                .map(existing -> {
                    existing.setApiToken(account.getApiToken());
                    existing.setDisplayPhoneNumber(account.getDisplayPhoneNumber());
                    existing.setTenantId(account.getTenantId());
                    return existing;
                })
                .orElse(account);

        WhatsAppAccountEntity saved = repository.save(entityToSave);
        return ResponseEntity.ok(saved);
    }

    @GetMapping
    public ResponseEntity<List<WhatsAppAccountEntity>> listAccounts() {
        return ResponseEntity.ok(repository.findAll());
    }
}