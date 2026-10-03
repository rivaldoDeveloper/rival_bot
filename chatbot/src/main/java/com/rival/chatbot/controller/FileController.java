package com.rival.chatbot.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

@RestController
@CrossOrigin(origins = "http://localhost:4200")
public class FileController {

    private static final Logger log = LoggerFactory.getLogger(FileController.class);
    private final String UPLOAD_DIR = "uploads/";

    // ✅ NOVO: Endpoint para receber o arquivo do Angular (Flow Engine) e salvar no disco
    @PostMapping("/api/v1/files/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            File directory = new File(UPLOAD_DIR);
            if (!directory.exists()) {
                directory.mkdirs();
            }

            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".")
                    ? originalFilename.substring(originalFilename.lastIndexOf("."))
                    : "";
            String newFileName = UUID.randomUUID().toString() + extension;

            Path filePath = Paths.get(UPLOAD_DIR + newFileName);
            Files.write(filePath, file.getBytes());

            String fileDownloadUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/uploads/")
                    .path(newFileName)
                    .toUriString();

            log.info("Mídia do Flow Engine guardada com sucesso: {}", fileDownloadUri);

            return ResponseEntity.ok(Map.of("url", fileDownloadUri));

        } catch (IOException e) {
            log.error("Erro ao guardar o ficheiro", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Falha ao processar o upload: " + e.getMessage()));
        }
    }

    // O SEU MÉTODO EXISTENTE (Lê e serve o arquivo para o Angular/WhatsApp)
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