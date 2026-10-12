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
public class EvolutionInstanceController {

    private final EvolutionInstanceService evolutionInstanceService;

    public EvolutionInstanceController(EvolutionInstanceService evolutionInstanceService) {
        this.evolutionInstanceService = evolutionInstanceService;
    }

    @Operation(summary = "Criar Instancia e Gerar QR", description = "Inicia uma nova sessao na Evolution API e devolve o QR Code em base64.")
    @PostMapping("/create")
    public ResponseEntity<Map<String, String>> createInstanceAndGetQR(@RequestParam UUID tenantId) {
        String base64Qr = evolutionInstanceService.createInstanceAndGetQR(tenantId);
        Map<String, String> response = new HashMap<>();
        response.put("qrCodeBase64", base64Qr);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Obter Novo QR Code", description = "Atualiza o QR Code caso o anterior tenha expirado durante a tentativa de conexao.")
    @GetMapping("/qr/{tenantId}")
    public ResponseEntity<Map<String, String>> getFreshQR(@PathVariable UUID tenantId) {
        String base64Qr = evolutionInstanceService.getFreshQR(tenantId);
        Map<String, String> response = new HashMap<>();
        response.put("qrCodeBase64", base64Qr);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Ativar Bot (Webhook)", description = "Mapeia as rotas de webhook na Evolution API para comecar a ouvir mensagens deste numero.")
    @PostMapping("/activate-bot")
    public ResponseEntity<Map<String, String>> activateBot(@RequestParam UUID tenantId) {
        evolutionInstanceService.activateBot(tenantId);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Robô ativado com sucesso!");
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Desconectar Instancia", description = "Exclui a sessao da Evolution API e desvincula o WhatsApp.")
    @DeleteMapping("/logout/{tenantId}")
    public ResponseEntity<Map<String, String>> logoutInstance(@PathVariable UUID tenantId) {
        evolutionInstanceService.logoutInstance(tenantId);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Desconectado com sucesso. Pode gerar um novo QR Code.");
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Emparelhar por Numero (Pairing Code)", description = "Cria uma instancia usando o numero de telefone para gerar o codigo nativo de pareamento do WhatsApp.")
    @PostMapping("/pairing-code")
    public ResponseEntity<Map<String, String>> getPairingCode(@RequestParam UUID tenantId, @RequestParam String phoneNumber) {
        String pairingCode = evolutionInstanceService.getPairingCode(tenantId, phoneNumber);
        Map<String, String> response = new HashMap<>();
        response.put("code", pairingCode != null ? pairingCode : "CÓDIGO_NÃO_GERADO");
        return ResponseEntity.ok(response);
    }
}