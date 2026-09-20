package com.rival.chatbot.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "flow_configs")
public class FlowConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    // Armazena o JSON gerado pelo React Flow / Node Editor
    @Column(columnDefinition = "TEXT", nullable = false)
    private String flowDataJson;

    public FlowConfigEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getFlowDataJson() { return flowDataJson; }
    public void setFlowDataJson(String flowDataJson) { this.flowDataJson = flowDataJson; }
}