package com.rival.user_service_api.service;

import com.rival.user_service_api.entity.TenantEntity;
import com.rival.user_service_api.repository.TenantRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;

    public TenantService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public TenantEntity createTenant(TenantEntity tenant) {
        // Aqui no futuro podemos adicionar validações (ex: verificar se o NIF já existe)
        return tenantRepository.save(tenant);
    }

    public List<TenantEntity> getAllTenants() {
        return tenantRepository.findAll();
    }
}