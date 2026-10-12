package com.rival.helpdesk_bff.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "user-service", url = "http://localhost:8081")
public interface UserClient {

    // Busca os dados do usuário logado na porta 8081
    @GetMapping("/api/users/{id}")
    Object getUserById(@PathVariable("id") UUID id);
}