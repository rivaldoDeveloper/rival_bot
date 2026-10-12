package com.rival.user_service_api.controller;

import com.rival.user_service_api.dto.TenantRequestDTO;
import com.rival.user_service_api.dto.TenantResponseDTO;
import com.rival.user_service_api.service.TenantService;
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

@RestController
@RequestMapping("/api/tenants")
@Tag(name = "Tenants", description = "API para gestão de Empresas/Clientes (Tenants)")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @Operation(summary = "Criar um novo Tenant")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Tenant criado com sucesso"),
            @ApiResponse(responseCode = "409", description = "Documento já registado")
    })
    @PostMapping
    public ResponseEntity<TenantResponseDTO> create(@RequestBody TenantRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tenantService.createTenant(dto));
    }

    @Operation(summary = "Listar todos os Tenants")
    @ApiResponse(responseCode = "200", description = "Lista paginada retornada com sucesso")
    @GetMapping
    public ResponseEntity<Page<TenantResponseDTO>> findAll(@ParameterObject Pageable pageable) {
        return ResponseEntity.ok(tenantService.getAllTenants(pageable));
    }
}