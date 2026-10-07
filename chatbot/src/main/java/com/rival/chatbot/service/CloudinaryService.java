package com.rival.chatbot.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
public class CloudinaryService {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryService.class);
    private final Cloudinary cloudinary;

    public CloudinaryService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    public String uploadFile(MultipartFile file) {
        try {
            return uploadToCloudinary(file.getBytes(), file.getOriginalFilename());
        } catch (IOException e) {
            log.error("Erro ao subir MultipartFile para o Cloudinary", e);
            throw new RuntimeException("Falha no upload para o Cloudinary: " + e.getMessage());
        }
    }

    public String uploadFile(java.io.File file) {
        try {
            return uploadToCloudinary(java.nio.file.Files.readAllBytes(file.toPath()), file.getName());
        } catch (IOException e) {
            log.error("Erro ao subir File para o Cloudinary", e);
            throw new RuntimeException("Falha no upload para o Cloudinary: " + e.getMessage());
        }
    }

    private String uploadToCloudinary(byte[] fileBytes, String originalName) throws IOException {
        if (originalName == null) originalName = "media";
        originalName = originalName.toLowerCase();

        String resourceType = "auto";
        if (originalName.endsWith(".mp4") || originalName.endsWith(".mov") || originalName.endsWith(".avi")) {
            resourceType = "video";
        } else if (originalName.endsWith(".webm") || originalName.endsWith(".ogg") || originalName.endsWith(".mp3") || originalName.endsWith(".m4a")) {
            resourceType = "video";
        } else if (originalName.endsWith(".jpg") || originalName.endsWith(".png") || originalName.endsWith(".jpeg") || originalName.endsWith(".webp")) {
            resourceType = "image";
        } else if (originalName.endsWith(".pdf") || originalName.endsWith(".doc")) {
            resourceType = "raw";
        }

        String cleanName = originalName.replaceAll("[^a-zA-Z0-9.-]", "_");
        int dotIndex = cleanName.lastIndexOf('.');
        String idName = dotIndex > 0 ? cleanName.substring(0, dotIndex) : cleanName;

        Map<String, Object> options = ObjectUtils.asMap(
                "resource_type", resourceType,
                "folder", "rivalchat_media",
                "public_id", UUID.randomUUID().toString().substring(0, 8) + "_" + idName
        );

        Map uploadResult = cloudinary.uploader().upload(fileBytes, options);
        return uploadResult.get("secure_url").toString();
    }

    public void deleteFile(String url) {
        if (url == null || url.isBlank()) return;
        try {
            String resourceType = url.contains("/video/") ? "video" : (url.contains("/raw/") ? "raw" : "image");
            String[] parts = url.split("/");
            String filenameWithExtension = parts[parts.length - 1];
            String folder = parts[parts.length - 2];

            int lastDotIndex = filenameWithExtension.lastIndexOf('.');
            String publicId = folder + "/" + (lastDotIndex != -1 ? filenameWithExtension.substring(0, lastDotIndex) : filenameWithExtension);

            Map<String, Object> options = ObjectUtils.asMap("resource_type", resourceType);
            Map result = cloudinary.uploader().destroy(publicId, options);
            log.info("Delete no Cloudinary executado para [{}]: {}", publicId, result.get("result"));
        } catch (Exception e) {
            log.error("Erro ao tentar deletar mídia do Cloudinary: " + url, e);
        }
    }
}