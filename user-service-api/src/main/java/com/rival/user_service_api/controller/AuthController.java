package com.rival.user_service_api.controller;

import com.rival.user_service_api.dto.UserRequestDTO;
import com.rival.user_service_api.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody UserRequestDTO request) {
        boolean isValid = userService.validatePassword(request.email(), request.password());

        if (isValid) {
            return ResponseEntity.ok(Map.of(
                    "message", "Login efetuado com sucesso",
                    "email", request.email(),
                    "token", "bearer_token_valido_123"
            ));
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Credenciais incorretas. Palavra-passe inválida."));
    }
}