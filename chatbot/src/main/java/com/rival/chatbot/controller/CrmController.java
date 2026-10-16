package com.rival.chatbot.controller;

import com.rival.chatbot.domain.CustomerDataEntity;
import com.rival.chatbot.repository.CustomerDataRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/crm/customers", "/api/crm/customers"})
@Tag(name = "CRM", description = "Endpoints de gestão dos clientes capturados pelo chatbot")
public class CrmController {

    private final CustomerDataRepository repository;

    public CrmController(CustomerDataRepository repository) {
        this.repository = repository;
    }

    @Operation(summary = "Listar clientes", description = "Retorna a lista de clientes capturados, com suporte a paginação e pesquisa por nome/canal/ID.")
    @GetMapping
    public ResponseEntity<Page<CustomerDataEntity>> getCustomers(
            @RequestHeader(value = "X-Tenant-Id", required = false) UUID tenantId,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("updatedAt").descending());

        if (tenantId != null) {
            return ResponseEntity.ok(repository.searchCustomersByTenant(tenantId, search.trim(), pageable));
        }

        return ResponseEntity.ok(repository.searchCustomers(search.trim(), pageable));
    }
}