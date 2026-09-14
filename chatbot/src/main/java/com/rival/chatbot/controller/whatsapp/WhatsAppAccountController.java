package com.rival.chatbot.controller.whatsapp;

import com.rival.chatbot.domain.whatsapp.WhatsAppAccountEntity;
import com.rival.chatbot.service.whatsapp.WhatsAppAccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/whatsapp/accounts")
public class WhatsAppAccountController {

    private final WhatsAppAccountService service;

    public WhatsAppAccountController(WhatsAppAccountService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<WhatsAppAccountEntity> registerOrUpdateAccount(@Valid @RequestBody WhatsAppAccountEntity account) {
        WhatsAppAccountEntity saved = service.saveOrUpdateAccount(account);
        return ResponseEntity.status(HttpStatus.OK).body(saved);
    }

    @GetMapping
    public ResponseEntity<List<WhatsAppAccountEntity>> listAccounts() {
        return ResponseEntity.ok(service.getAllAccounts());
    }
}