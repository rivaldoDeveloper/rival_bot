package com.rival.chatbot.controller;

import com.rival.chatbot.domain.NlpIntentEntity;
import com.rival.chatbot.dto.NlpIntentDTO;
import com.rival.chatbot.service.NlpTrainingService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/nlp/training")
public class NlpTrainingController {

    private final NlpTrainingService service;

    public NlpTrainingController(NlpTrainingService service) {
        this.service = service;
    }

    @PostMapping("/intents")
    public ResponseEntity<?> createIntent(@Valid @RequestBody NlpIntentDTO dto) {
        try {
            NlpIntentEntity saved = service.createIntent(dto);
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
    public ResponseEntity<?> updateIntent(@PathVariable UUID id, @Valid @RequestBody NlpIntentDTO dto) {
        return service.updateIntent(id, dto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/intents")
    public ResponseEntity<Page<NlpIntentEntity>> listIntents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        return ResponseEntity.ok(service.listIntents(page, size));
    }

    @DeleteMapping("/intents/{id}")
    public ResponseEntity<Void> deleteIntent(@PathVariable UUID id) {
        service.deleteIntent(id);
        return ResponseEntity.noContent().build();
    }
}