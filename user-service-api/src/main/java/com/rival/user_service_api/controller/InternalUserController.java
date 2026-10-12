package com.rival.user_service_api.controller;

import com.rival.user_service_api.dto.UserRequestDTO;
import com.rival.user_service_api.dto.UserResponseDTO;
import com.rival.user_service_api.service.UserService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/internal/users")
@Hidden // Esconde este controller do Swagger para que clientes externos não o vejam
public class InternalUserController {

    private final UserService userService;

    public InternalUserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/validate")
    public boolean validateCredentials(@RequestBody UserRequestDTO request) {
        // Lê o email e a senha diretamente do corpo do pedido
        return userService.validatePassword(request.email(), request.password());
    }

    // ADICIONAMOS ESTE MÉTODO AQUI PARA O BFF CONSEGUIR CHAMAR!
    @GetMapping("/{id}")
    public UserResponseDTO getUserById(@PathVariable UUID id) {
        return userService.getUserById(id);
    }
}