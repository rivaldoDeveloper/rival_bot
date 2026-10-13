package com.rival.api_gateway.filter;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Predicate;

@Component
public class RouteValidator {

    public static final List<String> openApiEndpoints = List.of(
            "/api/auth/login",
            "/v3/api-docs",
            "/swagger-ui",
            "/api/v1/meta/webhook",
            "/api/v1/whatsapp/webhook",
            "/ws-chat" // Libera o handshake inicial do WebSocket STOMP/SockJS
    );

    public Predicate<ServerHttpRequest> isSecured =
            request -> openApiEndpoints
                    .stream()
                    .noneMatch(uri -> request.getURI().getPath().contains(uri));
}