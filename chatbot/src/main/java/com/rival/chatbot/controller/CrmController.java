package com.rival.chatbot.controller;

import com.rival.chatbot.domain.CustomerDataEntity;
import com.rival.chatbot.repository.CustomerDataRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/crm/customers")
public class CrmController {

    private final CustomerDataRepository repository;

    public CrmController(CustomerDataRepository repository) {
        this.repository = repository;
    }

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