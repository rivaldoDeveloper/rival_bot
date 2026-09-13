package com.rival.chatbot.controller.telegram;

import com.rival.chatbot.domain.telegram.TelegramBotConfigEntity;
import com.rival.chatbot.service.telegram.TelegramBotConfigService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/telegram/bots")
public class TelegramBotConfigController {

    private final TelegramBotConfigService service;

    public TelegramBotConfigController(TelegramBotConfigService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TelegramBotConfigEntity> registerOrUpdateBot(@RequestBody TelegramBotConfigEntity botConfig) {
        TelegramBotConfigEntity saved = service.saveOrUpdateBot(botConfig);
        return ResponseEntity.status(HttpStatus.OK).body(saved);
    }

    @GetMapping
    public ResponseEntity<List<TelegramBotConfigEntity>> listBots() {
        return ResponseEntity.ok(service.getAllBots());
    }
}