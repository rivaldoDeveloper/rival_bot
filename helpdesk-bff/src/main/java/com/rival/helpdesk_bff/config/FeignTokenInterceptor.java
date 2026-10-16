package com.rival.helpdesk_bff.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignTokenInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();

            // Reenvia o Token JWT para os microsserviços
            String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                template.header(HttpHeaders.AUTHORIZATION, authorizationHeader);
            }

            // Reenvia o Tenant ID do utilizador logado para isolamento de dados
            String tenantIdHeader = request.getHeader("X-Tenant-Id");
            if (tenantIdHeader != null && !tenantIdHeader.isBlank()) {
                template.header("X-Tenant-Id", tenantIdHeader);
            }
        }
    }
}