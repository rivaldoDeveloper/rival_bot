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

@RestController
@RequestMapping("/api/v1/crm/customers")
@Tag(name = "CRM", description = "Endpoints de gestao dos clientes capturados pelo chatbot")
public class CrmController {

    private final CustomerDataRepository repository;

    public CrmController(CustomerDataRepository repository) {
        this.repository = repository;
    }

    @Operation(summary = "Listar clientes", description = "Retorna a lista de clientes capturados, com suporte a paginacao e pesquisa por nome/canal/ID.")
    @GetMapping
    public ResponseEntity<Page<CustomerDataEntity>> getCustomers(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {

        // Ordenar pelos últimos clientes a interagirem
        Pageable pageable = PageRequest.of(page, size, Sort.by("updatedAt").descending());

        if (search == null || search.isBlank()) {
            return ResponseEntity.ok(repository.findAll(pageable));
        }
        return ResponseEntity.ok(repository.searchCustomers(search.trim(), pageable));
    }
}