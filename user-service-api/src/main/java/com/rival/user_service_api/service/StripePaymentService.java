package com.rival.user_service_api.service;

import com.rival.user_service_api.dto.payment.CheckoutResponseDTO;
import com.rival.user_service_api.dto.payment.CreateCheckoutRequestDTO;
import com.rival.user_service_api.entity.TenantEntity;
import com.rival.user_service_api.repository.TenantRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.checkout.Session;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StripePaymentService {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentService.class);

    @Value("${stripe.api-key}")
    private String stripeApiKey;

    @Value("${stripe.success-url}")
    private String successUrl;

    @Value("${stripe.cancel-url}")
    private String cancelUrl;

    private final TenantRepository tenantRepository;

    public StripePaymentService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeApiKey;
    }

    @Transactional
    public CheckoutResponseDTO createSubscriptionCheckout(CreateCheckoutRequestDTO dto) throws StripeException {
        TenantEntity tenant = tenantRepository.findById(dto.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Tenant não encontrado com ID: " + dto.tenantId()));

        String customerId = tenant.getStripeCustomerId();

        // Se o Tenant ainda não tiver um Customer na Stripe, cria um novo
        if (customerId == null || customerId.isBlank()) {
            CustomerCreateParams customerParams = CustomerCreateParams.builder()
                    .setName(tenant.getName())
                    .putMetadata("tenantId", tenant.getId().toString())
                    .build();

            Customer stripeCustomer = Customer.create(customerParams);
            customerId = stripeCustomer.getId();
            tenant.setStripeCustomerId(customerId);
            tenantRepository.save(tenant);
        }

        // Cria a sessão de Checkout segura na Stripe
        SessionCreateParams sessionParams = SessionCreateParams.builder()
                .setCustomer(customerId)
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setPrice(dto.priceId())
                                .setQuantity(1L)
                                .build()
                )
                .putMetadata("tenantId", tenant.getId().toString())
                .build();

        Session session = Session.create(sessionParams);
        log.info("Sessão de Checkout criada para Tenant {}: {}", tenant.getName(), session.getUrl());

        return new CheckoutResponseDTO(session.getUrl());
    }
}