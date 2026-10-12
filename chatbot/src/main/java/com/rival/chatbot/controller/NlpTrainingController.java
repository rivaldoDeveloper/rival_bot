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
@Tag(name = "NLP Training", description = "Endpoints para o treinamento e gestão da rede neural local (Intenções e Respostas)")
public class NlpTrainingController {

    private final NlpTrainingService service;

    public NlpTrainingController(NlpTrainingService service) {
        this.service = service;
    }

    @Operation(summary = "Criar Intenção", description = "Injeta uma nova diretriz/intenção na base de conhecimento da IA.")
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

    @Operation(summary = "Atualizar Intenção", description = "Substitui os dados de uma intenção existente através do seu ID.")
    @PutMapping("/intents/{id}")
    public ResponseEntity<?> updateIntent(@PathVariable UUID id, @Valid @RequestBody NlpIntentDTO dto) {
        return service.updateIntent(id, dto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Listar Intenções", description = "Retorna a memória ativa da IA (lista de intenções) com paginação.")
    @GetMapping("/intents")
    public ResponseEntity<Page<NlpIntentEntity>> listIntents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        return ResponseEntity.ok(service.listIntents(page, size));
    }

    @Operation(summary = "Excluir Intenção", description = "Remove permanentemente uma intenção da rede neural local.")
    @DeleteMapping("/intents/{id}")
    public ResponseEntity<Void> deleteIntent(@PathVariable UUID id) {
        service.deleteIntent(id);
        return ResponseEntity.noContent().build();
    }
}