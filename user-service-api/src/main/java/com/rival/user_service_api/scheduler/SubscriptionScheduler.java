package com.rival.user_service_api.scheduler;

import com.rival.user_service_api.entity.TenantEntity;
import com.rival.user_service_api.enums.SubscriptionStatus;
import com.rival.user_service_api.repository.TenantRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class SubscriptionScheduler {

    private final TenantRepository tenantRepository;

    public SubscriptionScheduler(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    // Para testarmos agora, ele vai rodar a cada 1 MINUTO (60000 milissegundos)
    // Em produção, usaríamos algo como @Scheduled(cron = "0 0 0 * * *") para rodar à meia-noite
    @Scheduled(fixedRate = 60000)
    public void verifyOverdueSubscriptions() {
        System.out.println("🤖 [ROBÔ] A iniciar verificação paginada de subscrições...");

        List<SubscriptionStatus> statusAtivos = List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIAL);

        int numeroPagina = 0;
        int tamanhoLote = 100; // Processa 100 clientes de cada vez
        Page<TenantEntity> paginaDevedores;

        do {
            paginaDevedores = tenantRepository.findByDueDateBeforeAndSubscriptionStatusIn(
                    LocalDate.now(),
                    statusAtivos,
                    PageRequest.of(numeroPagina, tamanhoLote)
            );

            for (TenantEntity cliente : paginaDevedores.getContent()) {
                cliente.setSubscriptionStatus(SubscriptionStatus.OVERDUE);
                tenantRepository.save(cliente);
                System.out.println("🚨 [ROBÔ] Empresa bloqueada: " + cliente.getName());
            }

            numeroPagina++; // Avança para o próximo lote de 100

        } while (paginaDevedores.hasNext()); // Repete até as páginas acabarem
    }
}