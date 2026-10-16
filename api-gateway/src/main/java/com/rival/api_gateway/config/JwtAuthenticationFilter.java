package com.rival.api_gateway.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Component
public class JwtAuthenticationFilter implements GlobalFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Value("${jwt.secret:ChaveSecretaSuperSeguraComMaisDe32CaracteresRivalChat2026!}")
    private String secret;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 1. Libera solicitações OPTIONS (Preflight CORS)
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return chain.filter(exchange);
        }

        // 2. Libera rotas públicas e do Chat embarcado/Widget
        if (path.contains("/api/auth/") ||
                path.contains("/api/v1/auth/") ||
                path.contains("/api/v1/chat/send-flow") ||
                path.contains("/api/v1/chat/send") ||
                path.contains("/api/v1/chat/history") ||
                path.contains("/api/v1/files/upload") ||
                path.contains("/webhook") ||
                path.contains("/widget") ||
                path.contains("/ws-chat")) {
            return chain.filter(exchange);
        }

        // 3. Valida cabeçalho Authorization para rotas privadas
        if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
            log.warn("Gateway Bloqueou: Cabeçalho Authorization ausente na rota {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Gateway Bloqueou: Formato de token inválido na rota {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7).replace("\"", "").trim();

        try {
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String tenantId = claims.get("tenantId", String.class);
            String userId = claims.get("userId", String.class);

            ServerHttpRequest.Builder builder = request.mutate();
            if (tenantId != null) builder.header("X-Tenant-Id", tenantId);
            if (userId != null) builder.header("X-User-Id", userId);

            return chain.filter(exchange.mutate().request(builder.build()).build());
        } catch (Exception e) {
            log.error("Erro na validação do JWT no Gateway na rota {}: {}", path, e.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }
}