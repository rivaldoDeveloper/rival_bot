package com.rival.user_service_api.service;

import com.rival.user_service_api.entity.TenantEntity;
import com.rival.user_service_api.entity.UserEntity;
import com.rival.user_service_api.repository.TenantRepository;
import com.rival.user_service_api.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;

    public UserService(UserRepository userRepository, TenantRepository tenantRepository) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
    }

    public UserEntity createUser(UUID tenantId, UserEntity user) {
        // 1. Verifica se o Tenant existe antes de criar o utilizador
        TenantEntity tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant não encontrado com o ID: " + tenantId));

        // 2. Associa o utilizador a este Tenant
        user.setTenant(tenant);

        // 3. Guarda o utilizador na base de dados
        return userRepository.save(user);
    }

    public List<UserEntity> getUsersByTenant(UUID tenantId) {
        return userRepository.findByTenantId(tenantId);
    }
}