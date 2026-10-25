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

    // Sobrecarga para retrocompatibilidade
    public String generateOwnResponse(String userPrompt, String retrievedContext) {
        return generateOwnResponse(null, userPrompt, retrievedContext);
    }

    /**
     * Motor Sintetizador NLP em Java Puro.
     * Analisa a frase do usuário e formata a resposta baseada no conhecimento do PostgreSQL.
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

        return synthesizeNaturalResponse(userPrompt, retrievedContext);
    }

    private String synthesizeNaturalResponse(String userPrompt, String databaseContext) {
        String lowerPrompt = userPrompt.toLowerCase().trim();
        String cleanContext = databaseContext.trim();

        // 1. Identificação de Intenção e Sentimento
        boolean isQuestion = lowerPrompt.contains("?") || lowerPrompt.matches(".*\\b(como|qual|onde|por que|quanto|quando)\\b.*");
        boolean isConfirmation = lowerPrompt.matches("^(sim|ok|certo|entendi|perfeito).*");

        StringBuilder response = new StringBuilder();

        // 2. Montagem Dinâmica de Frase
        if (isConfirmation) {
            response.append("Maravilha! ");
        }

        // 3. Estruturação do contexto do banco
        String formattedContext = cleanContext.substring(0, 1).toUpperCase() + cleanContext.substring(1);

        if (isQuestion) {
            response.append("Sobre sua dúvida: ").append(formattedContext.substring(0, 1).toLowerCase()).append(formattedContext.substring(1));
        } else {
            response.append(formattedContext);
        }

        // 4. Fechamento conversacional
        if (!response.toString().endsWith(".") && !response.toString().endsWith("!") && !response.toString().endsWith("?")) {
            response.append(".");
        }

        return response.toString();
    }

    private String generateDynamicFallback(String userPrompt, BotSettingsEntity settings) {
        List<String> tokens = Arrays.asList(userPrompt.toLowerCase().split("[\\s\\p{Punct}]+"));

        // Extrai palavras-chave ignorando preposições e palavras curtas
        List<String> keywords = tokens.stream()
                .filter(w -> w.length() > 3 && !w.equals("como") && !w.equals("qual") && !w.equals("quero"))
                .collect(Collectors.toList());

        // TRAVA DE SEGURANÇA: Se o texto for muito longo (mais de 3 palavras-chave),
        // damos uma resposta educada sem repetir o testamento do cliente.
        if (keywords.size() > 3) {
            return settings.getFallbackLongMessage();
        }

        // Se for uma pergunta curta (ex: "tem garagem?"), ele repete o assunto de forma natural
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