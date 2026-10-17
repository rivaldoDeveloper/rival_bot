package com.rival.user_service_api.controller;

import com.rival.user_service_api.config.JwtUtil;
import com.rival.user_service_api.dto.UserRequestDTO;
import com.rival.user_service_api.entity.TenantEntity;
import com.rival.user_service_api.entity.UserEntity;
import com.rival.user_service_api.repository.TenantRepository;
import com.rival.user_service_api.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(UserRepository userRepository,
                          TenantRepository tenantRepository,
                          PasswordEncoder passwordEncoder,
                          JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody UserRequestDTO request) {
        Optional<UserEntity> userOpt = userRepository.findByEmail(request.email());

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Utilizador não encontrado."));
        }

        UserEntity user = userOpt.get();

        // Validação da palavra-passe encriptada com BCrypt
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Credenciais incorretas. Palavra-passe inválida."));
        }

        // Gera token JWT real criptografado
        String jwtToken = jwtUtil.generateToken(
                user.getId(),
                user.getEmail(),
                user.getTenant().getId(),
                user.getName()
        );

        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "name", user.getName(),
                "email", user.getEmail(),
                "tenantId", user.getTenant().getId(),
                "avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : "",
                "themePreference", user.getThemePreference() != null ? user.getThemePreference() : "light",
                "token", jwtToken,
                "message", "Login efetuado com sucesso"
        ));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody UserRequestDTO request) {
        // 1. Verifica se o e-mail já existe na base de dados
        if (userRepository.findByEmail(request.email()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "O e-mail especificado já está registado na plataforma.", "error", "Este e-mail já está em uso."));
        }

        // 2. Cria um novo Tenant para a organização/empresa gerando um documento temporário único
        TenantEntity tenant = new TenantEntity();
        tenant.setName(request.name() != null && !request.name().isBlank() ? request.name() : "Empresa de " + request.email());
        // Evita o erro 409 de chave duplicada (document único no PostgreSQL)
        tenant.setDocument("TEMP_" + UUID.randomUUID().toString().substring(0, 8));
        tenant = tenantRepository.save(tenant);

        // 3. Cria o novo utilizador com a palavra-passe encriptada (BCrypt)
        UserEntity newUser = new UserEntity();
        newUser.setName(request.name());
        newUser.setEmail(request.email());
        newUser.setPassword(passwordEncoder.encode(request.password()));
        newUser.setTenant(tenant);
        newUser.setThemePreference("light");
        UserEntity savedUser = userRepository.save(newUser);

        // 4. Gera o token JWT para início de sessão imediato
        String jwtToken = jwtUtil.generateToken(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getTenant().getId(),
                savedUser.getName()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", savedUser.getId(),
                "name", savedUser.getName(),
                "email", savedUser.getEmail(),
                "tenantId", savedUser.getTenant().getId(),
                "avatarUrl", "",
                "themePreference", savedUser.getThemePreference(),
                "token", jwtToken,
                "message", "Registo efetuado com sucesso"
        ));
    }
}