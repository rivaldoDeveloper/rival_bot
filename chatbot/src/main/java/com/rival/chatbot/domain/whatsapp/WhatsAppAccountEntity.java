package com.rival.chatbot.domain.whatsapp;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "whatsapp_accounts", indexes = {
        @Index(name = "idx_wa_instance_name", columnList = "instanceName")
})
public class WhatsAppAccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(unique = true)
    private String instanceName;

    @Column(columnDefinition = "TEXT")
    private String evolutionApiKey;

    private String displayPhoneNumber;

    // Colunas legadas mantidas para o PostgreSQL não quebrar
    @Column(columnDefinition = "TEXT")
    private String apiToken;
    private String phoneNumberId;

    public WhatsAppAccountEntity() {}

    /**
     * O Hibernate executa este método automaticamente ANTES de fazer o INSERT ou UPDATE.
     * Copiamos os valores novos para as colunas antigas e protegemos contra campos nulos.
     */
    @PrePersist
    @PreUpdate
    public void syncLegacyFields() {
        if (this.apiToken == null || this.apiToken.isBlank()) {
            this.apiToken = this.evolutionApiKey != null ? this.evolutionApiKey : "migrated_token";
        }
        if (this.phoneNumberId == null || this.phoneNumberId.isBlank()) {
            this.phoneNumberId = this.instanceName != null ? this.instanceName : "migrated_id";
        }
        // Previne o erro do display_phone_number nulo
        if (this.displayPhoneNumber == null || this.displayPhoneNumber.isBlank()) {
            this.displayPhoneNumber = "Pendente (QR Code)";
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getInstanceName() { return instanceName; }
    public void setInstanceName(String instanceName) { this.instanceName = instanceName; }

    public String getEvolutionApiKey() { return evolutionApiKey; }
    public void setEvolutionApiKey(String evolutionApiKey) { this.evolutionApiKey = evolutionApiKey; }

    public String getDisplayPhoneNumber() { return displayPhoneNumber; }
    public void setDisplayPhoneNumber(String displayPhoneNumber) { this.displayPhoneNumber = displayPhoneNumber; }

    public String getApiToken() { return apiToken; }
    public void setApiToken(String apiToken) { this.apiToken = apiToken; }

    public String getPhoneNumberId() { return phoneNumberId; }
    public void setPhoneNumberId(String phoneNumberId) { this.phoneNumberId = phoneNumberId; }
}