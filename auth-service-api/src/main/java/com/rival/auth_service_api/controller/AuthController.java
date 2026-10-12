package com.rival.auth_service_api.controller;

import com.rival.auth_service_api.client.UserClient;
import com.rival.auth_service_api.dto.AuthRequestDTO;
import com.rival.auth_service_api.dto.AuthResponseDTO;
import com.rival.auth_service_api.service.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticação", description = "API para Login e geração de Tokens JWT")
public class AuthController {

    private final UserClient userClient;
    private final JwtService jwtService;

    public AuthController(UserClient userClient, JwtService jwtService) {
        this.userClient = userClient;
        this.jwtService = jwtService;
    }

    @Operation(summary = "Fazer login", description = "Valida as credenciais enviadas e devolve um Token JWT de acesso.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login com sucesso. Token gerado."),
            @ApiResponse(responseCode = "401", description = "E-mail ou password incorretos.")
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@RequestBody AuthRequestDTO request) {
        // 1. Pergunta ao user-service se o e-mail e a senha estão corretos
        boolean isValid = userClient.validateUserCredentials(request);

        if (!isValid) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build(); // Erro 401: Senha errada
        }

        // 2. Se estiver correto, gera o Token usando o serviço que criámos
        String token = jwtService.generateToken(request.email());

        // 3. Devolve o token ao cliente
        return ResponseEntity.ok(new AuthResponseDTO(token));
    }
}