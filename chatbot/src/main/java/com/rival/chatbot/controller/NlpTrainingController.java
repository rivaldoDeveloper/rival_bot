package com.rival.chatbot.controller;

import com.rival.chatbot.domain.NlpIntentEntity;
import com.rival.chatbot.dto.NlpIntentDTO;
import com.rival.chatbot.repository.NlpIntentRepository;
import com.rival.chatbot.service.LocalVectorNlpService;
import com.rival.chatbot.service.NlpEngineService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public ResponseEntity<NlpIntentEntity> createIntent(@Valid @RequestBody NlpIntentDTO dto) {
        NlpIntentEntity entity = new NlpIntentEntity();
        entity.setName(dto.name());
        entity.setLanguage(dto.language());
        entity.setKeywords(dto.keywords());
        entity.setResponses(dto.responses());

        NlpIntentEntity saved = repository.save(entity);
        localVectorNlpService.clearIntentsCache();

        return ResponseEntity.ok(saved);
    }

    @GetMapping("/intents")
    public ResponseEntity<List<NlpIntentEntity>> listIntents() {
        return ResponseEntity.ok(repository.findAll());
    }
}