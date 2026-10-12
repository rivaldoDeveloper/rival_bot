package com.rival.user_service_api.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Este método vai "escutar" sempre que a base de dados estoirar por dados duplicados
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", LocalDateTime.now());
        errorResponse.put("status", HttpStatus.CONFLICT.value()); // Retorna 409 Conflict

        // Personaliza a mensagem se for o erro específico de e-mail ou documento duplicado
        String message = ex.getMostSpecificCause().getMessage();
        if (message != null && message.contains("Key (email)")) {
            errorResponse.put("error", "Este e-mail já está em uso.");
        } else if (message != null && message.contains("Key (document)")) {
            errorResponse.put("error", "Este documento já está registado noutra empresa.");
        } else {
            errorResponse.put("error", "Erro de dados duplicados.");
        }

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }
}