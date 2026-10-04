package com.rival.chatbot.domain.meta;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "meta_accounts", indexes = {
        @Index(name = "idx_meta_page_id", columnList = "pageId")
})
public class MetaAccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(unique = true, nullable = false)
    private String pageId; // ID da Página do Facebook ou IG Account ID

    @Column(columnDefinition = "TEXT", nullable = false)
    private String pageAccessToken;

    @Column(nullable = false)
    private String verifyToken; // Token configurado no painel da Meta para o Webhook

    @Column(length = 20)
    private String platform; // "MESSENGER" ou "INSTAGRAM"

    public MetaAccountEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getPageId() { return pageId; }
    public void setPageId(String pageId) { this.pageId = pageId; }
    public String getPageAccessToken() { return pageAccessToken; }
    public void setPageAccessToken(String pageAccessToken) { this.pageAccessToken = pageAccessToken; }
    public String getVerifyToken() { return verifyToken; }
    public void setVerifyToken(String verifyToken) { this.verifyToken = verifyToken; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
}