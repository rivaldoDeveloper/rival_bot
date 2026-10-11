package com.rival.user_service_api.controller;

import com.rival.user_service_api.entity.TenantEntity;
import com.rival.user_service_api.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenants")
// @Tag define o título e descrição desta secção no Swagger
@Tag(name = "Tenants", description = "API para gestão de Empresas/Clientes (Tenants)")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    // @Operation explica o que este endpoint faz
    @Operation(summary = "Criar um novo Tenant", description = "Regista uma nova empresa no sistema e gera um ID único (UUID).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Tenant criado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos enviados na requisição")
    })
    @PostMapping
    public ResponseEntity<TenantEntity> create(@RequestBody TenantEntity tenant) {
        TenantEntity savedTenant = tenantService.createTenant(tenant);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedTenant);
    }

    @Operation(summary = "Listar todos os Tenants", description = "Retorna a lista de todas as empresas registadas na base de dados.")
    @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso")
    @GetMapping
    public ResponseEntity<List<TenantEntity>> findAll() {
        return ResponseEntity.ok(tenantService.getAllTenants());
    }
}