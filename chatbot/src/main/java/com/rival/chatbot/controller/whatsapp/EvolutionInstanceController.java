package com.rival.chatbot.controller.whatsapp;

import com.rival.chatbot.service.whatsapp.EvolutionInstanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/whatsapp/instances")
public class EvolutionInstanceController {

    private final EvolutionInstanceService evolutionInstanceService;

    public EvolutionInstanceController(EvolutionInstanceService evolutionInstanceService) {
        this.evolutionInstanceService = evolutionInstanceService;
    }

    @PostMapping("/create")
    public ResponseEntity<Map<String, String>> createInstanceAndGetQR(@RequestParam UUID tenantId) {
        String base64Qr = evolutionInstanceService.createInstanceAndGetQR(tenantId);
        Map<String, String> response = new HashMap<>();
        response.put("qrCodeBase64", base64Qr);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/qr/{tenantId}")
    public ResponseEntity<Map<String, String>> getFreshQR(@PathVariable UUID tenantId) {
        String base64Qr = evolutionInstanceService.getFreshQR(tenantId);
        Map<String, String> response = new HashMap<>();
        response.put("qrCodeBase64", base64Qr);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/activate-bot")
    public ResponseEntity<Map<String, String>> activateBot(@RequestParam UUID tenantId) {
        evolutionInstanceService.activateBot(tenantId);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Robô ativado com sucesso!");
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/logout/{tenantId}")
    public ResponseEntity<Map<String, String>> logoutInstance(@PathVariable UUID tenantId) {
        evolutionInstanceService.logoutInstance(tenantId);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Desconectado com sucesso. Pode gerar um novo QR Code.");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/pairing-code")
    public ResponseEntity<Map<String, String>> getPairingCode(@RequestParam UUID tenantId, @RequestParam String phoneNumber) {
        String pairingCode = evolutionInstanceService.getPairingCode(tenantId, phoneNumber);
        Map<String, String> response = new HashMap<>();
        response.put("code", pairingCode != null ? pairingCode : "CÓDIGO_NÃO_GERADO");
        return ResponseEntity.ok(response);
    }
}