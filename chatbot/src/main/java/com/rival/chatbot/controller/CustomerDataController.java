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
@RequestMapping("/api/v1/customer-data")
@Tag(name = "Customer Data (CRM)", description = "Base de dados e extracao de clientes")
public class CustomerDataController {

    private final CustomerDataRepository customerDataRepository;

    public CustomerDataController(CustomerDataRepository customerDataRepository) {
        this.customerDataRepository = customerDataRepository;
    }

    @Operation(summary = "Listar Clientes (CRM)", description = "Retorna a lista paginada de clientes e dados extraidos pelo bot.")
    @GetMapping
    public ResponseEntity<Page<CustomerDataEntity>> getCustomers(
            @RequestParam(required = false, defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {

        // Ordena por updatedAt (campo existente na entidade)
        Pageable pageable = PageRequest.of(page, size, Sort.by("updatedAt").descending());

        return ResponseEntity.ok(customerDataRepository.searchCustomers(search != null ? search.trim() : "", pageable));
    }
}