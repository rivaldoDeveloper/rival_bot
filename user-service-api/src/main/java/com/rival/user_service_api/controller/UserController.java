package com.rival.user_service_api.controller;

import com.rival.user_service_api.dto.UserRequestDTO;
import com.rival.user_service_api.dto.UserResponseDTO;
import com.rival.user_service_api.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<UserResponseDTO> create(
            @PathVariable UUID tenantId,
            @RequestBody UserRequestDTO dto) {

        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(tenantId, dto));
    }

    @Operation(summary = "Listar Utilizadores por Tenant", description = "Retorna todos os utilizadores de uma Empresa específica com suporte a paginação.")
    @ApiResponse(responseCode = "200", description = "Página de utilizadores retornada com sucesso")
    @GetMapping
    public ResponseEntity<Page<UserResponseDTO>> findAllByTenant(
            @PathVariable UUID tenantId,
            @ParameterObject Pageable pageable) {

        return ResponseEntity.ok(userService.getUsersByTenant(tenantId, pageable));
    }

    @Operation(summary = "Buscar Utilizador por ID", description = "Retorna os detalhes de um utilizador específico.")
    @ApiResponse(responseCode = "200", description = "Utilizador encontrado")
    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDTO> findById(@PathVariable UUID id) {
        // Precisamos criar este método no UserService também!
        return ResponseEntity.ok(userService.getUserById(id));
    }
}