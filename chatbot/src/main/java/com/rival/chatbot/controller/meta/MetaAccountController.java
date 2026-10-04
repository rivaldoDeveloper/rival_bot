package com.rival.chatbot.controller.meta;

import com.rival.chatbot.domain.meta.MetaAccountEntity;
import com.rival.chatbot.service.meta.MetaAccountService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/meta/accounts")
public class MetaAccountController {

    private final MetaAccountService service;

    public MetaAccountController(MetaAccountService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<MetaAccountEntity> registerOrUpdateAccount(@RequestBody MetaAccountEntity account) {
        MetaAccountEntity saved = service.saveOrUpdateAccount(account);
        return ResponseEntity.status(HttpStatus.OK).body(saved);
    }

    @GetMapping
    public ResponseEntity<List<MetaAccountEntity>> listAccounts() {
        return ResponseEntity.ok(service.getAllAccounts());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccount(@PathVariable UUID id) {
        service.deleteAccount(id);
        return ResponseEntity.noContent().build();
    }
}