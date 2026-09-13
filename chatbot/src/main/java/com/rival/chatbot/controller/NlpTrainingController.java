package com.rival.chatbot.controller;

import com.rival.chatbot.domain.NlpIntentEntity;
import com.rival.chatbot.dto.NlpIntentDTO;
import com.rival.chatbot.repository.NlpIntentRepository;
import com.rival.chatbot.service.LocalVectorNlpService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/nlp/training")
public class NlpTrainingController {

    private final NlpIntentRepository repository;
    private final LocalVectorNlpService localVectorNlpService;

    public NlpTrainingController(NlpIntentRepository repository, LocalVectorNlpService localVectorNlpService) {
        this.repository = repository;
        this.localVectorNlpService = localVectorNlpService;
    }

    @PostMapping("/intents")
    public ResponseEntity<?> createIntent(@Valid @RequestBody NlpIntentDTO dto) {
        // 1. Busca por nome no repositório retornando lista
        List<NlpIntentEntity> existingIntents = repository.findByNameIgnoreCase(dto.name());

        // 2. Se a intenção já existir, impede o cadastro e retorna HTTP 400 Bad Request
        if (existingIntents != null && !existingIntents.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "status", 400,
                    "error", "Bad Request",
                    "message", "A intenção '" + dto.name() + "' já está cadastrada. Não é permitido salvar intenções duplicadas."
            ));
        }

        // 3. Caso seja nova, salva no banco com coleções mutáveis
        NlpIntentEntity entity = new NlpIntentEntity();
        entity.setName(dto.name());
        entity.setLanguage(dto.language());
        entity.setKeywords(dto.keywords() != null ? new ArrayList<>(dto.keywords()) : new ArrayList<>());
        entity.setResponses(dto.responses() != null ? new ArrayList<>(dto.responses()) : new ArrayList<>());

        NlpIntentEntity saved = repository.save(entity);

        // Invalida o Cache do Spring para atualizar o motor de PNL
        localVectorNlpService.clearIntentsCache();

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/intents")
    public ResponseEntity<List<NlpIntentEntity>> listIntents() {
        return ResponseEntity.ok(repository.findAll());
    }
}