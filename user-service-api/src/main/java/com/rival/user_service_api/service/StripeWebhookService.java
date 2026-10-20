package com.rival.user_service_api.service;

import com.rival.user_service_api.entity.TenantEntity;
import com.rival.user_service_api.enums.SubscriptionStatus;
import com.rival.user_service_api.repository.TenantRepository;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
public class StripeWebhookService {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookService.class);
    private final TenantRepository tenantRepository;

    public StripeWebhookService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Transactional
    public void processStripeEvent(Event event) {
        log.info("Evento recebido da Stripe: {}", event.getType());

        switch (event.getType()) {

            // 1. Checkout concluído com sucesso
            case "checkout.session.completed":
                Session session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);
                if (session != null && session.getMetadata() != null) {
                    String tenantIdStr = session.getMetadata().get("tenantId");
                    if (tenantIdStr != null) {
                        UUID tenantId = UUID.fromString(tenantIdStr);
                        tenantRepository.findById(tenantId).ifPresent(tenant -> {
                            tenant.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
                            tenant.setStripeSubscriptionId(session.getSubscription());
                            tenant.setDueDate(LocalDate.now().plusDays(30)); // Estende a validade por 30 dias
                            tenantRepository.save(tenant);
                            log.info("Tenant {} ATIVADO com sucesso via Stripe Checkout!", tenant.getName());
                        });
                    }
                }
                break;

            // 2. Fatura mensal PAGA
            case "invoice.paid":
                Invoice invoice = (Invoice) event.getDataObjectDeserializer().getObject().orElse(null);
                if (invoice != null && invoice.getCustomer() != null) {
                    String customerId = invoice.getCustomer();
                    tenantRepository.findAll().stream()
                            .filter(t -> customerId.equals(t.getStripeCustomerId()))
                            .findFirst()
                            .ifPresent(tenant -> {
                                tenant.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
                                tenant.setDueDate(LocalDate.now().plusDays(30));
                                tenantRepository.save(tenant);
                                log.info("Mensalidade renovada por +30 dias para o Tenant: {}", tenant.getName());
                            });
                }
                break;

            // 3. Pagamento Falhou (Inadimplente)
            case "invoice.payment_failed":
                Invoice failedInvoice = (Invoice) event.getDataObjectDeserializer().getObject().orElse(null);
                if (failedInvoice != null && failedInvoice.getCustomer() != null) {
                    String customerId = failedInvoice.getCustomer();
                    tenantRepository.findAll().stream()
                            .filter(t -> customerId.equals(t.getStripeCustomerId()))
                            .findFirst()
                            .ifPresent(tenant -> {
                                tenant.setSubscriptionStatus(SubscriptionStatus.OVERDUE);
                                tenantRepository.save(tenant);
                                log.warn("Pagamento FALHOU. Tenant marcado como OVERDUE: {}", tenant.getName());
                            });
                }
                break;

            // 4. Assinatura Cancelada
            case "customer.subscription.deleted":
                Subscription sub = (Subscription) event.getDataObjectDeserializer().getObject().orElse(null);
                if (sub != null && sub.getCustomer() != null) {
                    String customerId = sub.getCustomer();
                    tenantRepository.findAll().stream()
                            .filter(t -> customerId.equals(t.getStripeCustomerId()))
                            .findFirst()
                            .ifPresent(tenant -> {
                                tenant.setSubscriptionStatus(SubscriptionStatus.CANCELED);
                                tenant.setActive(false);
                                tenantRepository.save(tenant);
                                log.info("Assinatura CANCELADA para o Tenant: {}", tenant.getName());
                            });
                }
                break;

            default:
                log.debug("Evento Stripe não processado: {}", event.getType());
        }
    }
}