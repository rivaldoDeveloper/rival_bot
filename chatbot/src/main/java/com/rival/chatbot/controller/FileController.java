package com.rival.chatbot.controller;

import com.rival.chatbot.service.CloudinaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import java.util.Map;

@RestController
@CrossOrigin(origins = "http://localhost:4200")
@RequestMapping("/api/v1/files")
public class FileController {

    private static final Logger log = LoggerFactory.getLogger(FileController.class);
    private final CloudinaryService cloudinaryService;

    // Injeta o Cloudinary Service
    public FileController(CloudinaryService cloudinaryService) {
        this.cloudinaryService = cloudinaryService;
    }

    // UPLOAD PARA O CLOUDINARY
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            String cloudinaryUrl = cloudinaryService.uploadFile(file);
            log.info("Mídia salva no Cloudinary: {}", cloudinaryUrl);
            return ResponseEntity.ok(Map.of("url", cloudinaryUrl));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Falha ao processar o upload: " + e.getMessage()));
        }
    }

    // ENDPOINT PARA APAGAR MÍDIA DA NUVEM (CORRIGIDO PARA ACEITAR URL ENCODED)
    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteFile(@RequestParam("url") String url) {
        try {
            // Decodifica a URL porque o Angular pode enviar com caracteres %2F etc.
            String decodedUrl = URLDecoder.decode(url, StandardCharsets.UTF_8.name());
            cloudinaryService.deleteFile(decodedUrl);
            log.info("Arquivo deletado com sucesso do Cloudinary: {}", decodedUrl);
            return ResponseEntity.ok(Map.of("message", "Ficheiro apagado com sucesso do Cloudinary."));
        } catch (Exception e) {
            log.error("Erro interno ao deletar arquivo do Cloudinary", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}