package com.rival.chatbot.controller.meta;

import com.rival.chatbot.domain.meta.MetaAccountEntity;
import com.rival.chatbot.service.meta.MetaAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/meta/accounts")
@Tag(name = "Meta Accounts", description = "Endpoints para gerenciamento de contas do Facebook Messenger e Instagram")
public class MetaAccountController {

    private final MetaAccountService service;

    public MetaAccountController(MetaAccountService service) {
        this.service = service;
    }

    @Operation(summary = "Registrar/Atualizar Conta", description = "Adiciona uma nova conta da Meta ou atualiza uma existente (Page ID, Tokens).")
    @PostMapping
    public ResponseEntity<MetaAccountEntity> registerOrUpdateAccount(
            @RequestHeader(value = "X-Tenant-Id", required = false) UUID tenantId,
            @RequestBody MetaAccountEntity account) {

        /* ========================================================================
         * MUDANÇA MULTI-TENANCY:
         * Atribui o TenantId do header para salvar a conta do Facebook/Instagram.
         * ======================================================================== */
        if (tenantId != null) {
            account.setTenantId(tenantId);
        }
        MetaAccountEntity saved = service.saveOrUpdateAccount(account);
        return ResponseEntity.status(HttpStatus.OK).body(saved);
    }

    /* ========================================================================
     * MUDANÇA MULTI-TENANCY:
     * Traz exclusivamente as contas do Facebook/Instagram do Tenant atual.
     * ======================================================================== */
    @Operation(summary = "Listar Contas", description = "Retorna a lista de todas as contas da Meta cadastradas no sistema.")
    @GetMapping
    public ResponseEntity<List<MetaAccountEntity>> listAccounts(
            @RequestHeader(value = "X-Tenant-Id", required = false) UUID tenantId) {

        if (tenantId != null) {
            return ResponseEntity.ok(service.getAccountsByTenant(tenantId));
        }
        return ResponseEntity.ok(service.getAllAccounts());
    }

    @Operation(summary = "Deletar Conta", description = "Remove uma conta especifica da Meta do banco de dados.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccount(@PathVariable UUID id) {
        service.deleteAccount(id);
        return ResponseEntity.noContent().build();
    }
}