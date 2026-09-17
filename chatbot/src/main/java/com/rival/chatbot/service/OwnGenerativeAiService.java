package com.rival.chatbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OwnGenerativeAiService {

    private static final Logger log = LoggerFactory.getLogger(OwnGenerativeAiService.class);

    /**
     * Motor Sintetizador NLP em Java Puro.
     * Analisa a frase do usuário e formata a resposta baseada no conhecimento do PostgreSQL.
     */
    public String generateOwnResponse(String userPrompt, String retrievedContext) {
        if (userPrompt == null || userPrompt.isBlank()) {
            return "Como posso ajudar você hoje?";
        }

        if (retrievedContext == null || retrievedContext.contains("Desculpe") || retrievedContext.contains("Ainda não fui treinado")) {
            return generateDynamicFallback(userPrompt);
        }

        return synthesizeNaturalResponse(userPrompt, retrievedContext);
    }

    private String synthesizeNaturalResponse(String userPrompt, String databaseContext) {
        String lowerPrompt = userPrompt.toLowerCase().trim();
        String cleanContext = databaseContext.trim();

        // 1. Identificação de Intenção e Sentimento
        boolean isGreeting = lowerPrompt.matches("^(oi|olá|ola|bom dia|boa tarde|boa noite|opa).*");
        boolean isQuestion = lowerPrompt.contains("?") || lowerPrompt.matches(".*\\b(como|qual|onde|por que|quanto|quando)\\b.*");
        boolean isConfirmation = lowerPrompt.matches("^(sim|ok|certo|entendi|perfeito).*");

        StringBuilder response = new StringBuilder();

        // 2. Montagem Dinâmica de Frase
        if (isGreeting) {
            response.append("Olá! ");
        } else if (isConfirmation) {
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

    private String generateDynamicFallback(String userPrompt) {
        List<String> tokens = Arrays.asList(userPrompt.toLowerCase().split("[\\s\\p{Punct}]+"));

        // Extrai palavras-chave ignorando preposições
        List<String> keywords = tokens.stream()
                .filter(w -> w.length() > 3 && !w.equals("como") && !w.equals("qual") && !w.equals("quero"))
                .collect(Collectors.toList());

        if (!keywords.isEmpty()) {
            String subject = String.join(" ", keywords);
            return "Eu consultei nossa base na nuvem, mas ainda não tenho detalhes específicos sobre '" + subject + "'. Posso ajudar com outro assunto?";
        }

        return "Analisei o que você disse, mas não encontrei diretrizes no banco de dados. Pode detalhar melhor?";
    }
}