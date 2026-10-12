package com.rival.chatbot.controller.whatsapp;

import com.rival.chatbot.domain.whatsapp.WhatsAppAccountEntity;
import com.rival.chatbot.service.whatsapp.WhatsAppAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/whatsapp/accounts")
@Tag(name = "WhatsApp Accounts", description = "Endpoints para gerenciamento local de metadados das contas de WhatsApp")
public class WhatsAppAccountController {

    private final WhatsAppAccountService service;

    public WhatsAppAccountController(WhatsAppAccountService service) {
        this.service = service;
    }

    @Operation(summary = "Salvar Conta Local", description = "Armazena ou atualiza no banco local os detalhes da instancia criada pela Evolution API.")
    @PostMapping
    public ResponseEntity<WhatsAppAccountEntity> registerOrUpdateAccount(@Valid @RequestBody WhatsAppAccountEntity account) {
        WhatsAppAccountEntity saved = service.saveOrUpdateAccount(account);
        return ResponseEntity.status(HttpStatus.OK).body(saved);
    }

    @Operation(summary = "Listar Contas de WhatsApp", description = "Traz todas as contas mapeadas no banco local do SaaS.")
    @GetMapping
    public ResponseEntity<List<WhatsAppAccountEntity>> listAccounts() {
        return ResponseEntity.ok(service.getAllAccounts());
    }
}