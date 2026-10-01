package com.rival.chatbot.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@CrossOrigin(origins = "http://localhost:4200")
public class FileController {

    private static final Logger log = LoggerFactory.getLogger(FileController.class);

    @GetMapping("/uploads/{filename:.+}")
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
        try {
            Path file = Paths.get("uploads").resolve(filename).normalize();
            Resource resource = new UrlResource(file.toUri());

            if (resource.exists() || resource.isReadable()) {
                String contentType = Files.probeContentType(file);

                // ✅ FORÇAR TIPO CORRETO PARA EVITAR CONFLITOS NO BROWSER
                String name = filename.toLowerCase();
                if (name.contains("gravacao_audio") || name.endsWith(".webm") || name.endsWith(".ogg") || name.endsWith(".mp3")) {
                    contentType = "audio/webm"; // Mantemos .webm no mimetype local pois gravámos nativamente assim
                } else if (name.endsWith(".mp4")) {
                    contentType = "video/mp4";
                } else if (name.endsWith(".pdf")) {
                    contentType = "application/pdf";
                } else if (contentType == null) {
                    contentType = "application/octet-stream";
                }

                log.info("Servindo arquivo de mídia para o Painel: {} ({})", filename, contentType);

                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                        .body(resource);
            } else {
                log.warn("Arquivo não encontrado no disco: {}", filename);
            }
        } catch (Exception e) {
            log.error("Erro ao tentar servir o arquivo: {}", filename, e);
        }

        return ResponseEntity.notFound().build();
    }
}