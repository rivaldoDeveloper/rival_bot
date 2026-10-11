package com.rival.user_service_api.entity;

import com.rival.user_service_api.enums.SubscriptionStatus;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tb_tenant")
public class TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 50)
    private String document; // NIF, CNPJ ou documento de identificação

    @Column(nullable = false)
    private boolean active = true;

    // --- NOVOS CAMPOS DE PAGAMENTO ---

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status", nullable = false)
    private SubscriptionStatus subscriptionStatus;

    @Column(name = "due_date")
    private LocalDate dueDate; // Data limite para pagar a mensalidade

    // ---------------------------------

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();

        // Regra de negócio inicial: Quando um Tenant é criado, começa com 14 dias de TRIAL (teste grátis)
        if (this.subscriptionStatus == null) {
            this.subscriptionStatus = SubscriptionStatus.TRIAL;
            this.dueDate = LocalDate.now().plusDays(14);
        }
    }

    // --- Construtores ---
    public TenantEntity() {}

    public TenantEntity(String name, String document) {
        this.name = name;
        this.document = document;
        this.active = true;
    }

    // --- Getters e Setters ---
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDocument() { return document; }
    public void setDocument(String document) { this.document = document; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public SubscriptionStatus getSubscriptionStatus() { return subscriptionStatus; }
    public void setSubscriptionStatus(SubscriptionStatus subscriptionStatus) { this.subscriptionStatus = subscriptionStatus; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}