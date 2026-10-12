package com.rival.user_service_api.service;

import com.rival.user_service_api.dto.TenantRequestDTO;
import com.rival.user_service_api.dto.TenantResponseDTO;
import com.rival.user_service_api.entity.TenantEntity;
import com.rival.user_service_api.repository.TenantRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;

    public TenantService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public TenantResponseDTO createTenant(TenantRequestDTO dto) {
        TenantEntity tenant = new TenantEntity(dto.name(), dto.document());
        TenantEntity savedTenant = tenantRepository.save(tenant);

        return new TenantResponseDTO(
                savedTenant.getId(),
                savedTenant.getName(),
                savedTenant.getDocument(),
                savedTenant.isActive(),
                savedTenant.getSubscriptionStatus(),
                savedTenant.getDueDate()
        );
    }

    public Page<TenantResponseDTO> getAllTenants(Pageable pageable) {
        return tenantRepository.findAll(pageable)
                .map(t -> new TenantResponseDTO(
                        t.getId(),
                        t.getName(),
                        t.getDocument(),
                        t.isActive(),
                        t.getSubscriptionStatus(),
                        t.getDueDate()
                ));
    }
}