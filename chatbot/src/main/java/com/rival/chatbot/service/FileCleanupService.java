package com.rival.chatbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class FileCleanupService {

    private static final Logger log = LoggerFactory.getLogger(FileCleanupService.class);
    private static final String UPLOADS_DIR = "uploads";

    // O Cron "0 0 3 * * *" significa: Rodar todos os dias às 03:00 da manhã
    @Scheduled(cron = "0 0 3 * * *")
    public void cleanOldUploads() {
        log.info("Iniciando rotina de limpeza da pasta de uploads...");

        File dir = new File(UPLOADS_DIR);
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }

        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            log.info("Nenhum ficheiro para limpar.");
            return;
        }

        // Calcula o timestamp de exatamente 24 horas atrás
        long twentyFourHoursAgo = Instant.now().minus(24, ChronoUnit.HOURS).toEpochMilli();
        int deletedCount = 0;

        for (File file : files) {
            // Se a última modificação do ficheiro for mais antiga que 24h
            if (file.lastModified() < twentyFourHoursAgo) {
                if (file.delete()) {
                    deletedCount++;
                } else {
                    log.warn("Falha ao apagar o ficheiro temporário: {}", file.getName());
                }
            }
        }

        log.info("Rotina concluída. Ficheiros antigos apagados: {}", deletedCount);
    }
}