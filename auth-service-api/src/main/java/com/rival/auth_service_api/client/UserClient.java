package com.rival.auth_service_api.client;

import com.rival.auth_service_api.dto.AuthRequestDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "user-service-api", url = "http://localhost:8081")
public interface UserClient {

    // Agora usamos POST e enviamos a senha no corpo do pedido, escondida dos logs
    @PostMapping("/api/internal/users/validate")
    boolean validateUserCredentials(@RequestBody AuthRequestDTO request);
}