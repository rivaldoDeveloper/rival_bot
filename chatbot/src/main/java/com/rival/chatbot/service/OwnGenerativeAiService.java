package com.rival.chatbot.service;

import com.rival.chatbot.domain.BotSettingsEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OwnGenerativeAiService {

    private static final Logger log = LoggerFactory.getLogger(OwnGenerativeAiService.class);
    private final BotSettingsService botSettingsService;

    public OwnGenerativeAiService(BotSettingsService botSettingsService) {
        this.botSettingsService = botSettingsService;
    }

    public String generateOwnResponse(String userPrompt, String retrievedContext) {
        return generateOwnResponse(null, userPrompt, retrievedContext);
    }

    /**
     * Motor Sintetizador Conversacional Estilo Gemini.
     * Combina múltiplos contextos da NLP e adapta o tom da resposta com naturalidade.
     */
    public String generateOwnResponse(UUID tenantId, String userPrompt, String retrievedContext) {
        BotSettingsEntity settings = botSettingsService.getSettingsByTenant(tenantId);

        if (userPrompt == null || userPrompt.isBlank()) {
            return settings.getFallbackGenericMessage();
        }

        if ("__UNTRAINED__".equals(retrievedContext) || (retrievedContext != null && retrievedContext.contains("Ainda não fui treinado"))) {
            return settings.getUntrainMessage();
        }

        if (retrievedContext == null || "__NO_MATCH__".equals(retrievedContext) || retrievedContext.contains("Desculpe, não localizei")) {
            return generateDynamicFallback(userPrompt, settings);
        }

        return synthesizeGeminiStyleResponse(userPrompt, retrievedContext);
    }

    private String synthesizeGeminiStyleResponse(String userPrompt, String databaseContext) {
        String lowerPrompt = userPrompt.toLowerCase().trim();
        String[] contextParts = databaseContext.split("\\|\\|NLP_MULTI\\|\\|");

        String primaryPart = ensureProperPunctuation(contextParts[0].trim());
        StringBuilder response = new StringBuilder();

        // 1. Se o utilizador começou com uma saudação + pergunta (e a resposta já não começa com saudação),
        // adiciona uma abertura calorosa natural estilo Gemini
        boolean userGreeted = lowerPrompt.matches("^(oi|olá|ola|bom dia|boa tarde|boa noite|opa|e ai|hey|hello|hola)\\b.*");
        boolean responseAlreadyGreets = primaryPart.toLowerCase().matches("^(oi|olá|ola|bom dia|boa tarde|boa noite|bem-vindo|seja bem).*");
        boolean isMultiWordPrompt = lowerPrompt.split("\\s+").length >= 3;

        if (userGreeted && isMultiWordPrompt && !responseAlreadyGreets) {
            if (lowerPrompt.startsWith("bom dia")) {
                response.append("Bom dia! ");
            } else if (lowerPrompt.startsWith("boa tarde")) {
                response.append("Boa tarde! ");
            } else if (lowerPrompt.startsWith("boa noite")) {
                response.append("Boa noite! ");
            } else {
                response.append("Olá! ");
            }
        }

        // 2. Adiciona a resposta principal da diretriz exatamente com a fluidez que o cliente treinou
        response.append(primaryPart);

        // 3. Se o motor detectou uma 2ª intenção na mesma mensagem do utilizador, conecta de forma inteligente!
        if (contextParts.length > 1 && !contextParts[1].isBlank()) {
            String secondPart = ensureProperPunctuation(contextParts[1].trim());
            // Remove perguntas de fechamento genéricas da primeira parte se houver uma segunda parte complementar
            response.append("\n\nAlém disso, ").append( Character.toLowerCase(secondPart.charAt(0)) ).append(secondPart.substring(1));
        }

        return response.toString();
    }

    private String ensureProperPunctuation(String text) {
        if (text == null || text.isBlank()) return "";
        String clean = text.trim();
        clean = Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
        if (!clean.endsWith(".") && !clean.endsWith("!") && !clean.endsWith("?") && !clean.endsWith(":")) {
            clean += ".";
        }
        return clean;
    }

    private String generateDynamicFallback(String userPrompt, BotSettingsEntity settings) {
        List<String> tokens = Arrays.asList(userPrompt.toLowerCase().split("[\\s\\p{Punct}]+"));

        List<String> keywords = tokens.stream()
                .filter(w -> w.length() > 3 && !w.equals("como") && !w.equals("qual") && !w.equals("quero") && !w.equals("voces") && !w.equals("voce"))
                .collect(Collectors.toList());

        if (keywords.size() > 3) {
            return settings.getFallbackLongMessage();
        }

        if (!keywords.isEmpty()) {
            String subject = String.join(" ", keywords);
            String template = settings.getFallbackShortMessage();
            if (template != null && template.contains("{assunto}")) {
                return template.replace("{assunto}", subject);
            }
            return template;
        }

        return settings.getFallbackGenericMessage();
    }
}