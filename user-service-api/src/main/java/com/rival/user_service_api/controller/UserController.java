package com.rival.user_service_api.controller;

import com.rival.user_service_api.entity.UserEntity;
import com.rival.user_service_api.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenants/{tenantId}/users")
@Tag(name = "Users", description = "API para gestão de Utilizadores vinculados a uma Empresa (Tenant)")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Criar um novo Utilizador", description = "Regista um utilizador vinculando-o obrigatoriamente a uma Empresa (Tenant) existente.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Utilizador criado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Tenant não encontrado ou inválido")
    })
    @PostMapping
    public ResponseEntity<UserEntity> create(
            @PathVariable UUID tenantId,
            @RequestBody UserEntity user) {

        UserEntity savedUser = userService.createUser(tenantId, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedUser);
    }

    @Operation(summary = "Listar Utilizadores por Tenant", description = "Retorna todos os utilizadores que pertencem a uma Empresa específica.")
    @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso")
    @GetMapping
    public ResponseEntity<List<UserEntity>> findAllByTenant(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(userService.getUsersByTenant(tenantId));
    }
}