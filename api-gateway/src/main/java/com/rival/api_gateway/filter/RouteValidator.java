package com.rival.api_gateway.filter;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Predicate;

@Component
public class RouteValidator {

    public static final List<String> openApiEndpoints = List.of(
            "/api/auth/",
            "/api/v1/auth/",
            "/api/v1/chat/send-flow",
            "/api/v1/chat/send",
            "/api/v1/chat/history",
            "/api/v1/files/upload",
            "/v3/api-docs",
            "/swagger-ui",
            "/api/v1/meta/webhook",
            "/api/v1/whatsapp/webhook",
            "/ws-chat"
    );

    public Predicate<ServerHttpRequest> isSecured =
            request -> openApiEndpoints
                    .stream()
                    .noneMatch(uri -> request.getURI().getPath().contains(uri));
}