package com.rival.helpdesk_bff.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(name = "user-service", url = "http://localhost:8081")
public interface UserClient {

    // Buscar um único usuário pelo ID (Rota interna)
    @GetMapping("/api/internal/users/{id}")
    Object getUserById(@PathVariable("id") UUID id);

    // Listar todos os tenants (empresas)
    @GetMapping("/api/tenants")
    Object getAllTenants(@RequestParam(value = "page", defaultValue = "0") int page,
                         @RequestParam(value = "size", defaultValue = "10") int size);

    // Listar usuários de um tenant específico
    @GetMapping("/api/tenants/{tenantId}/users")
    Object getUsersByTenant(@PathVariable("tenantId") UUID tenantId,
                            @RequestParam(value = "page", defaultValue = "0") int page,
                            @RequestParam(value = "size", defaultValue = "10") int size);
}