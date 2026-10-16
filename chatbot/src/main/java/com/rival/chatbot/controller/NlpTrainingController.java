package com.rival.chatbot.controller;

import com.rival.chatbot.domain.NlpIntentEntity;
import com.rival.chatbot.dto.NlpIntentDTO;
import com.rival.chatbot.service.NlpTrainingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/nlp/training")
@Tag(name = "NLP Training", description = "Endpoints para o treinamento e gestão da rede neural local")
public class NlpTrainingController {

    private final NlpTrainingService service;

    public NlpTrainingController(NlpTrainingService service) {
        this.service = service;
    }

    @PostMapping("/intents")
    public ResponseEntity<?> createIntent(
            @RequestHeader(value = "X-Tenant-Id", required = false) UUID tenantId,
            @Valid @RequestBody NlpIntentDTO dto) {
        try {
            NlpIntentEntity saved = service.createIntent(tenantId, dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "status", 400,
                    "error", "Bad Request",
                    "message", e.getMessage()
            ));
        }
    }

    @PutMapping("/intents/{id}")
    public ResponseEntity<?> updateIntent(
            @RequestHeader(value = "X-Tenant-Id", required = false) UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody NlpIntentDTO dto) {
        return service.updateIntent(tenantId, id, dto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/intents")
    public ResponseEntity<Page<NlpIntentEntity>> listIntents(
            @RequestHeader(value = "X-Tenant-Id", required = false) UUID tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(service.listIntents(tenantId, page, size));
    }

    @DeleteMapping("/intents/{id}")
    public ResponseEntity<Void> deleteIntent(
            @RequestHeader(value = "X-Tenant-Id", required = false) UUID tenantId,
            @PathVariable UUID id) {
        service.deleteIntent(id);
        return ResponseEntity.noContent().build();
    }
}