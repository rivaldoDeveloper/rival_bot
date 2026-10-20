package com.rival.user_service_api.controller;

import com.rival.user_service_api.dto.payment.CheckoutResponseDTO;
import com.rival.user_service_api.dto.payment.CreateCheckoutRequestDTO;
import com.rival.user_service_api.service.StripePaymentService;
import com.rival.user_service_api.service.StripeWebhookService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payments", description = "Endpoints para gestão de assinaturas e checkout via Stripe")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final StripePaymentService stripePaymentService;
    private final StripeWebhookService stripeWebhookService;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    public PaymentController(StripePaymentService stripePaymentService, StripeWebhookService stripeWebhookService) {
        this.stripePaymentService = stripePaymentService;
        this.stripeWebhookService = stripeWebhookService;
    }

    @Operation(summary = "Criar Checkout de Assinatura", description = "Gera a URL de pagamento da Stripe para assinar um plano.")
    @PostMapping("/checkout")
    public ResponseEntity<?> createCheckout(@Valid @RequestBody CreateCheckoutRequestDTO dto) {
        try {
            CheckoutResponseDTO response = stripePaymentService.createSubscriptionCheckout(dto);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Erro ao gerar Checkout da Stripe: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(e.getMessage());
        }
    }

    @Operation(summary = "Webhook Oficial da Stripe", description = "Recebe eventos automáticos da Stripe sobre pagamentos e cancelamentos.")
    @PostMapping("/webhook")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Assinatura de Webhook Inválida");
        }
        stripeWebhookService.processStripeEvent(event);
        return ResponseEntity.ok("Evento processado");
    }
}