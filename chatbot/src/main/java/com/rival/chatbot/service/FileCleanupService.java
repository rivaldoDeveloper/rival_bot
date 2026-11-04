package com.rival.chatbot.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class FileCleanupService {

    private static final Logger log = LoggerFactory.getLogger(FileCleanupService.class);

    /**
     * 1. DELETA TODOS OS ARQUIVOS DE UPLOADS AO INICIAR / REINICIAR O SERVIDOR
     */
    @PostConstruct
    public void cleanOnStartup() {
        log.info(">> [FileCleanupService] Servidor iniciando: limpando arquivos da pasta uploads...");
        cleanAllUploads();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void cleanWhenReady() {
        cleanAllUploads();
    }

    /**
     * 2. DELETA TODOS OS ARQUIVOS DE UPLOADS AO PARAR O SERVIDOR
     */
    @PreDestroy
    public void cleanOnShutdown() {
        cleanAllUploads();
    }

    /**
     * 3. SCHEDULE AUTOMÁTICO DE 1 EM 1 HORA:
     * Roda a cada 1 hora e apaga todos os arquivos acumulados na pasta uploads.
     */
    @Scheduled(fixedRate = 1, initialDelay = 1, timeUnit = TimeUnit.HOURS)
    public void scheduledHourlyCleanup() {
        log.info(">> [FileCleanupService] Executando limpeza agendada de 1 em 1 hora na pasta uploads...");
        cleanAllUploads();
    }

    private Set<Path> getUploadDirectories() {
        Set<Path> dirs = new LinkedHashSet<>();
        Path cwd = Paths.get("").toAbsolutePath().normalize();

        dirs.add(cwd.resolve("uploads"));
        dirs.add(cwd.resolve(Paths.get("chatbot", "uploads")));

        try {
            Path codePath = Paths.get(FileCleanupService.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path moduleRoot = (codePath.getParent() != null && codePath.getParent().getParent() != null)
                    ? codePath.getParent().getParent()
                    : codePath;
            dirs.add(moduleRoot.resolve("uploads").toAbsolutePath().normalize());
        } catch (URISyntaxException | SecurityException ignored) {}

        return dirs;
    }

    public void cleanAllUploads() {
        int deletedCount = 0;

        for (Path dirPath : getUploadDirectories()) {
            File dir = dirPath.toFile();
            if (!dir.exists() || !dir.isDirectory()) {
                continue;
            }

            File[] files = dir.listFiles();
            if (files == null || files.length == 0) {
                continue;
            }

            for (File file : files) {
                if (file.isFile() && !".gitkeep".equals(file.getName())) {
                    try {
                        if (Files.deleteIfExists(file.toPath()) || file.delete()) {
                            deletedCount++;
                        }
                    } catch (Exception e) {
                        file.deleteOnExit();
                    }
                }
            }
        }

        log.info(">> [FileCleanupService] Limpeza concluída! Arquivos deletados de uploads: {}", deletedCount);
    }
}