package com.rival.chatbot.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "whatsapp_accounts", indexes = {
        @Index(name = "idx_wa_phone_number_id", columnList = "phoneNumberId")
})
public class WhatsAppAccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false, unique = true)
    private String phoneNumberId; // ID fornecido no painel da Meta para esta empresa

    @Column(nullable = false)
    private String displayPhoneNumber; // O número real (ex: 5511999999999)

    @Column(nullable = false, columnDefinition = "TEXT")
    private String apiToken; // Token de acesso Bearer da Meta para esta empresa

    public WhatsAppAccountEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getPhoneNumberId() { return phoneNumberId; }
    public void setPhoneNumberId(String phoneNumberId) { this.phoneNumberId = phoneNumberId; }

    public String getDisplayPhoneNumber() { return displayPhoneNumber; }
    public void setDisplayPhoneNumber(String displayPhoneNumber) { this.displayPhoneNumber = displayPhoneNumber; }

    public String getApiToken() { return apiToken; }
    public void setApiToken(String apiToken) { this.apiToken = apiToken; }
}