package com.rival.chatbot.controller.telegram;

import com.rival.chatbot.domain.telegram.TelegramBotConfigEntity;
import com.rival.chatbot.service.telegram.TelegramBotConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/telegram/bots")
@Tag(name = "Telegram Bots", description = "Endpoints de gerenciamento de robos multiplos do Telegram")
public class TelegramBotConfigController {

    private final TelegramBotConfigService service;

    public TelegramBotConfigController(TelegramBotConfigService service) {
        this.service = service;
    }

    @Operation(summary = "Salvar Configuracao do Bot", description = "Adiciona um novo bot ou atualiza o Token HTTP API gerado pelo BotFather.")
    @PostMapping
    public ResponseEntity<TelegramBotConfigEntity> registerOrUpdateBot(
            @RequestHeader(value = "X-Tenant-Id", required = false) UUID tenantId,
            @RequestBody TelegramBotConfigEntity botConfig) {

        /* ========================================================================
         * MUDANÇA MULTI-TENANCY:
         * Garante que o bot seja criado apontando para o tenantId real do header.
         * ======================================================================== */
        if (tenantId != null) {
            botConfig.setTenantId(tenantId);
        }
        TelegramBotConfigEntity saved = service.saveOrUpdateBot(botConfig);
        return ResponseEntity.status(HttpStatus.OK).body(saved);
    }

    /* ========================================================================
     * MUDANÇA MULTI-TENANCY:
     * Filtra a listagem de Bots por Tenant.
     * ======================================================================== */
    @Operation(summary = "Listar Bots do Telegram", description = "Recupera todas as configuracoes de bots de Telegram vinculadas aos tenants.")
    @GetMapping
    public ResponseEntity<List<TelegramBotConfigEntity>> listBots(
            @RequestHeader(value = "X-Tenant-Id", required = false) UUID tenantId) {

        if (tenantId != null) {
            return ResponseEntity.ok(service.getBotsByTenant(tenantId));
        }
        return ResponseEntity.ok(service.getAllBots());
    }

    @Operation(summary = "Deletar Bot", description = "Remove as configuracoes de um robo especifico do Telegram.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBot(@PathVariable UUID id) {
        service.deleteBot(id);
        return ResponseEntity.noContent().build();
    }
}