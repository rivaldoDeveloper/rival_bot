package com.rival.user_service_api.enums;

public enum SubscriptionStatus {
    TRIAL,      // Cliente está no período de teste grátis
    ACTIVE,     // Mensalidade paga e em dia
    OVERDUE,    // Pagamento em atraso (Pode ser bloqueado)
    CANCELED    // Cliente cancelou o serviço
}