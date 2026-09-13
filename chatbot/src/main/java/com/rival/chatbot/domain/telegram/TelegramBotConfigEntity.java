package com.rival.chatbot.domain.telegram;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "telegram_bot_configs")
public class TelegramBotConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false, unique = true)
    private String botUsername;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String botToken;

    @Column(nullable = false)
    private boolean active = true;

    public TelegramBotConfigEntity() {}

    /**
     * Executado automaticamente pelo Hibernate antes de salvar no banco.
     * Se o tenantId não for informado (null), gera um UUID aleatório automaticamente.
     */
    @PrePersist
    public void prePersist() {
        if (this.tenantId == null) {
            this.tenantId = UUID.randomUUID();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getBotUsername() { return botUsername; }
    public void setBotUsername(String botUsername) { this.botUsername = botUsername; }
    public String getBotToken() { return botToken; }
    public void setBotToken(String botToken) { this.botToken = botToken; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}