package com.rival.user_service_api.repository;

import com.rival.user_service_api.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    // Método para procurar o utilizador na hora do Login
    Optional<UserEntity> findByEmail(String email);

    // Método para listar todos os utilizadores de um Tenant específico
    List<UserEntity> findByTenantId(UUID tenantId);
}