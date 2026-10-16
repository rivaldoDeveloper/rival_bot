package com.rival.user_service_api.service;

import com.rival.user_service_api.dto.UpdatePasswordRequestDTO;
import com.rival.user_service_api.dto.UpdateProfileRequestDTO;
import com.rival.user_service_api.dto.UserRequestDTO;
import com.rival.user_service_api.dto.UserResponseDTO;
import com.rival.user_service_api.entity.TenantEntity;
import com.rival.user_service_api.entity.UserEntity;
import com.rival.user_service_api.repository.TenantRepository;
import com.rival.user_service_api.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, TenantRepository tenantRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponseDTO createUser(UUID tenantId, UserRequestDTO dto) {
        // 1. Verifica se o Tenant existe antes de criar o utilizador
        TenantEntity tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant não encontrado com o ID: " + tenantId));

        // 2. Associa o utilizador a este Tenant
        UserEntity user = new UserEntity();
        user.setName(dto.name());
        user.setEmail(dto.email());
        user.setPassword(passwordEncoder.encode(dto.password()));

        // 3. Guarda o utilizador na base de dados
        user.setTenant(tenant);
        user.setActive(true);

        UserEntity savedUser = userRepository.save(user);

        return new UserResponseDTO(savedUser.getId(), savedUser.getName(), savedUser.getEmail(), savedUser.isActive());
    }

    public Page<UserResponseDTO> getUsersByTenant(UUID tenantId, Pageable pageable) {
        return userRepository.findByTenantId(tenantId, pageable)
                .map(u -> new UserResponseDTO(u.getId(), u.getName(), u.getEmail(), u.isActive()));
    }

    // Valida se a senha enviada em texto limpo corresponde à senha encriptada (BCrypt) na base de dados
    public boolean validatePassword(String email, String rawPassword) {
        return userRepository.findByEmail(email)
                .map(user -> passwordEncoder.matches(rawPassword, user.getPassword()))
                .orElse(false);
    }

    public UserResponseDTO getUserById(UUID userId) {
        return userRepository.findById(userId)
                .map(u -> new UserResponseDTO(u.getId(), u.getName(), u.getEmail(), u.isActive()))
                .orElseThrow(() -> new RuntimeException("Utilizador não encontrado."));
    }

    @Transactional
    public UserResponseDTO updateProfile(UUID userId, UpdateProfileRequestDTO dto) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Utilizador não encontrado."));

        // Se o e-mail mudou, verifica se já existe outro utilizador com este e-mail
        if (!user.getEmail().equalsIgnoreCase(dto.email())) {
            userRepository.findByEmail(dto.email()).ifPresent(existing -> {
                throw new IllegalArgumentException("O e-mail especificado já está em uso.");
            });
            user.setEmail(dto.email());
        }

        user.setName(dto.name());
        UserEntity updated = userRepository.save(user);
        return new UserResponseDTO(updated.getId(), updated.getName(), updated.getEmail(), updated.isActive());
    }

    @Transactional
    public void updatePassword(UUID userId, UpdatePasswordRequestDTO dto) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Utilizador não encontrado."));

        // 1. Valida se a palavra-passe atual coincide com o hash do BCrypt
        if (!passwordEncoder.matches(dto.currentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("A palavra-passe atual introduzida está incorreta.");
        }

        // 2. Encripta e guarda a nova palavra-passe
        user.setPassword(passwordEncoder.encode(dto.newPassword()));
        userRepository.save(user);
    }
}