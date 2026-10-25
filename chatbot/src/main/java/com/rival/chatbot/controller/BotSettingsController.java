package com.rival.chatbot.controller;

import com.rival.chatbot.domain.BotSettingsEntity;
import com.rival.chatbot.service.BotSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bot-settings")
@Tag(name = "Bot Settings", description = "Configuração dinâmica de mensagens padrão da IA e do Flow Engine")
public class BotSettingsController {

    private final BotSettingsService service;

    public BotSettingsController(BotSettingsService service) {
        this.service = service;
    }

    @Operation(summary = "Obter configurações de mensagens do Bot")
    @GetMapping
    public ResponseEntity<BotSettingsEntity> getSettings(
            @RequestHeader(value = "X-Tenant-Id", required = false) UUID tenantId,
            @RequestParam(value = "tenantId", required = false) UUID queryTenantId) {
        UUID targetTenant = tenantId != null ? tenantId : queryTenantId;
        return ResponseEntity.ok(service.getSettingsByTenant(targetTenant));
    }

    @Operation(summary = "Salvar configurações de mensagens do Bot")
    @PostMapping
    public ResponseEntity<BotSettingsEntity> saveSettings(
            @RequestHeader(value = "X-Tenant-Id", required = false) UUID tenantId,
            @RequestBody BotSettingsEntity payload) {
        UUID targetTenant = tenantId != null ? tenantId : payload.getTenantId();
        if (targetTenant == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(service.saveSettings(targetTenant, payload));
    }
}