package com.rival.chatbot.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "bot_settings", indexes = {
        @Index(name = "idx_bot_settings_tenant", columnList = "tenantId", unique = true)
})
public class BotSettingsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID tenantId;

    @Column(columnDefinition = "TEXT")
    private String untrainMessage = "Ainda não tenho diretrizes cadastradas. Como posso ajudar?";

    @Column(columnDefinition = "TEXT")
    private String fallbackShortMessage = "Eu consultei nossa base de dados, mas ainda não tenho detalhes específicos sobre '{assunto}'. Posso ajudar com outro assunto?";

    @Column(columnDefinition = "TEXT")
    private String fallbackLongMessage = "Ainda não tenho as diretrizes exatas para responder a essa questão de forma completa. Posso transferir para um especialista ou ajudar com outro assunto?";

    @Column(columnDefinition = "TEXT")
    private String fallbackGenericMessage = "Analisei o que você disse, mas não encontrei diretrizes no banco de dados. Pode detalhar melhor?";

    @Column(columnDefinition = "TEXT")
    private String humanHandoffMessage = "Entendido! Estou transferindo seu atendimento para um operador humano.";

    @Column(columnDefinition = "TEXT")
    private String flowHandoffMessage = "A transferir para um assistente humano...";

    @Column(columnDefinition = "TEXT")
    private String handoffKeywords = "atendente, humano, suporte";

    @Column(columnDefinition = "TEXT")
    private String resetFlowKeywords = "oi, olá, ola, menu, inicio, início, começar, comecar, start, voltar";

    public BotSettingsEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getUntrainMessage() { return untrainMessage; }
    public void setUntrainMessage(String untrainMessage) { this.untrainMessage = untrainMessage; }

    public String getFallbackShortMessage() { return fallbackShortMessage; }
    public void setFallbackShortMessage(String fallbackShortMessage) { this.fallbackShortMessage = fallbackShortMessage; }

    public String getFallbackLongMessage() { return fallbackLongMessage; }
    public void setFallbackLongMessage(String fallbackLongMessage) { this.fallbackLongMessage = fallbackLongMessage; }

    public String getFallbackGenericMessage() { return fallbackGenericMessage; }
    public void setFallbackGenericMessage(String fallbackGenericMessage) { this.fallbackGenericMessage = fallbackGenericMessage; }

    public String getHumanHandoffMessage() { return humanHandoffMessage; }
    public void setHumanHandoffMessage(String humanHandoffMessage) { this.humanHandoffMessage = humanHandoffMessage; }

    public String getFlowHandoffMessage() { return flowHandoffMessage; }
    public void setFlowHandoffMessage(String flowHandoffMessage) { this.flowHandoffMessage = flowHandoffMessage; }

    public String getHandoffKeywords() { return handoffKeywords; }
    public void setHandoffKeywords(String handoffKeywords) { this.handoffKeywords = handoffKeywords; }

    public String getResetFlowKeywords() { return resetFlowKeywords; }
    public void setResetFlowKeywords(String resetFlowKeywords) { this.resetFlowKeywords = resetFlowKeywords; }
}