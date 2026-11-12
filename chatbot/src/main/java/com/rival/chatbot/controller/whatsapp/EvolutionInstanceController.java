package com.rival.chatbot.controller.whatsapp;

import com.rival.chatbot.service.whatsapp.EvolutionInstanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/whatsapp/instances")
@Tag(name = "WhatsApp Evolution Instances", description = "Endpoints de operacao e conexao de instancias do WhatsApp (Baileys) via Evolution API")
@SuppressWarnings({"rawtypes", "unchecked"})
public class EvolutionInstanceController {

    private final EvolutionInstanceService evolutionInstanceService;

    public EvolutionInstanceController(EvolutionInstanceService evolutionInstanceService) {
        this.evolutionInstanceService = evolutionInstanceService;
    }

    @Operation(summary = "Criar Instancia e Gerar QR", description = "Inicia uma nova sessao na Evolution API e devolve o QR Code em base64.")
    @PostMapping("/create")
    public ResponseEntity createInstanceAndGetQR(@RequestParam UUID tenantId) {
        String base64Qr = evolutionInstanceService.createInstanceAndGetQR(tenantId);
        Map response = new HashMap();
        response.put("qrCodeBase64", base64Qr);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Obter Novo QR Code", description = "Atualiza o QR Code caso o anterior tenha expirado durante a tentativa de conexao.")
    @GetMapping("/qr/{tenantId}")
    public ResponseEntity getFreshQR(@PathVariable UUID tenantId) {
        String base64Qr = evolutionInstanceService.getFreshQR(tenantId);
        Map response = new HashMap();
        response.put("qrCodeBase64", base64Qr);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Verificar Status da Conexao", description = "Consulta na Evolution API se a instancia do tenant ja esta conectada (open).")
    @GetMapping("/status/{tenantId}")
    public ResponseEntity getStatusByPath(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(evolutionInstanceService.getConnectionStatus(tenantId));
    }

    @Operation(summary = "Verificar Status da Conexao (Query)", description = "Consulta na Evolution API se a instancia do tenant ja esta conectada (open).")
    @GetMapping("/status")
    public ResponseEntity getStatusByQuery(
            @RequestParam(required = false) UUID tenantId,
            @RequestHeader(value = "X-Tenant-Id", required = false) UUID headerTenantId) {
        UUID target = tenantId != null ? tenantId : headerTenantId;
        if (target == null) {
            Map disconnected = new HashMap();
            disconnected.put("connected", false);
            disconnected.put("state", "close");
            disconnected.put("status", "DISCONNECTED");
            return ResponseEntity.ok(disconnected);
        }
        return ResponseEntity.ok(evolutionInstanceService.getConnectionStatus(target));
    }

    @Operation(summary = "Ativar Bot (Webhook)", description = "Mapeia as rotas de webhook na Evolution API para comecar a ouvir mensagens deste numero.")
    @PostMapping("/activate-bot")
    public ResponseEntity activateBot(@RequestParam UUID tenantId) {
        evolutionInstanceService.activateBot(tenantId);
        Map response = new HashMap();
        response.put("message", "Robo ativado com sucesso!");
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Desconectar Instancia", description = "Exclui a sessao da Evolution API e desvincula o WhatsApp.")
    @RequestMapping(value = "/logout/{tenantId}", method = {RequestMethod.DELETE, RequestMethod.POST})
    public ResponseEntity logoutInstance(@PathVariable UUID tenantId) {
        evolutionInstanceService.logoutInstance(tenantId);
        Map response = new HashMap();
        response.put("message", "Desconectado com sucesso. Pode gerar um novo QR Code.");
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Emparelhar por Numero (Pairing Code)", description = "Cria uma instancia usando o numero de telefone para gerar o codigo nativo de pareamento do WhatsApp.")
    @PostMapping("/pairing-code")
    public ResponseEntity getPairingCode(@RequestParam UUID tenantId, @RequestParam String phoneNumber) {
        String pairingCode = evolutionInstanceService.getPairingCode(tenantId, phoneNumber);
        Map response = new HashMap();
        response.put("code", pairingCode != null ? pairingCode : "CODIGO_NAO_GERADO");
        return ResponseEntity.ok(response);
    }
}