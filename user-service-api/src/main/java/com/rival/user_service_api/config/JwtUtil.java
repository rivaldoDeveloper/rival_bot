package com.rival.user_service_api.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    @Value("${jwt.secret:ChaveSecretaSuperSeguraComMaisDe32CaracteresRivalChat2026!}")
    private String secret;

    public String generateToken(UUID userId, String email, UUID tenantId, String name) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        long expirationMillis = 86400000L; // 24 Horas

        return Jwts.builder()
                .setHeaderParam("typ", "JWT")
                .setSubject(email)
                .claim("userId", userId != null ? userId.toString() : "")
                .claim("tenantId", tenantId != null ? tenantId.toString() : "")
                .claim("name", name != null ? name : "")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMillis))
                .signWith(key, SignatureAlgorithm.HS256) // <- FORÇA HS256
                .compact();
    }
}