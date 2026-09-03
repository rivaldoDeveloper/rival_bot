package com.rival.chatbot.service;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    @Value("${tesseract.datapath:/usr/share/tesseract-ocr/4.00/tessdata}")
    private String tessDataPath;

    @Value("${tesseract.language:por}")
    private String defaultLanguage;

    /**
     * Extrai o texto a partir de um arquivo físico de imagem (JPG, PNG, TIFF)
     */
    public String extractTextFromImageFile(File imageFile) {
        if (imageFile == null || !imageFile.exists()) {
            log.warn("Arquivo de imagem inválido ou inexistente fornecido para OCR.");
            return "";
        }

        try {
            ITesseract tesseract = initTesseract();
            String extractedText = tesseract.doOCR(imageFile);
            log.info("OCR concluído com sucesso a partir do arquivo: {}", imageFile.getName());
            return cleanExtractedText(extractedText);
        } catch (TesseractException e) {
            log.error("Erro ao processar OCR no arquivo de imagem: {}", imageFile.getAbsolutePath(), e);
            return "";
        }
    }

    /**
     * Extrai o texto a partir de uma String codificada em Base64 (comum em APIs REST)
     */
    public String extractTextFromBase64(String base64Image) {
        if (base64Image == null || base64Image.isBlank()) {
            return "";
        }

        try {
            // Remove o cabeçalho data:image/png;base64, se houver
            String cleanBase64 = base64Image.contains(",") ? base64Image.split(",")[1] : base64Image;
            byte[] imageBytes = Base64.getDecoder().decode(cleanBase64);

            try (InputStream is = new ByteArrayInputStream(imageBytes)) {
                BufferedImage bufferedImage = ImageIO.read(is);
                if (bufferedImage == null) {
                    log.warn("Não foi possível decodificar a imagem da string Base64.");
                    return "";
                }

                ITesseract tesseract = initTesseract();
                String extractedText = tesseract.doOCR(bufferedImage);
                log.info("OCR concluído com sucesso a partir de buffer Base64.");
                return cleanExtractedText(extractedText);
            }
        } catch (IOException | TesseractException e) {
            log.error("Erro ao processar OCR a partir de imagem Base64", e);
            return "";
        }
    }

    /**
     * Inicializa a instância do Tesseract com idioma e caminho do tessdata
     */
    private ITesseract initTesseract() {
        ITesseract tesseract = new Tesseract();
        tesseract.setDatapath(tessDataPath);
        tesseract.setLanguage(defaultLanguage); // 'por' para Português, 'eng' para Inglês
        return tesseract;
    }

    /**
     * Sanitiza o texto extraído do OCR
     */
    private String cleanExtractedText(String text) {
        if (text == null) return "";
        // Remove quebras de linha excessivas e caracteres residuais de leitura
        return text.replaceAll("[\\r\\n]+", " ").replaceAll("\\s+", " ").trim();
    }
}