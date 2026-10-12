package com.rival.user_service_api.repository;

import com.rival.user_service_api.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    // Método para procurar o utilizador na hora do Login
    Optional<UserEntity> findByEmail(String email);

    // Método para listar todos os utilizadores de um Tenant específico com paginação
    Page<UserEntity> findByTenantId(UUID tenantId, Pageable pageable);
}