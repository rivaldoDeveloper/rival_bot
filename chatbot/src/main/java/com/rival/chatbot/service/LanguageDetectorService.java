package com.rival.chatbot.service;

import com.rival.chatbot.util.TextNormalizerUtil;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/*LEGADO NÂO MEXER, NEM APAGAR*/
//@Service
public class LanguageDetectorService {

    private static final Map<String, Set<String>> STOP_WORDS_BY_LANG = Map.of(
            "pt", Set.of("de", "a", "o", "que", "e", "do", "da", "em", "um", "para", "com", "nao", "uma", "os", "no", "se", "na", "por", "mais", "as", "dos", "como", "mas", "ao", "ele", "das"),
            "en", Set.of("the", "be", "to", "of", "and", "a", "in", "that", "have", "i", "it", "for", "not", "on", "with", "he", "as", "you", "do", "at", "this", "but", "his", "by", "from"),
            "es", Set.of("de", "la", "que", "el", "en", "y", "a", "los", "del", "se", "las", "por", "un", "para", "con", "no", "una", "su", "al", "lo", "como", "mas", "pero", "sus", "le")
    );

    public String detectLanguage(String text) {
        if (text == null || text.isBlank()) return "pt";

        String normalizedText = TextNormalizerUtil.cleanAndDeduplicate(text);
        String[] words = normalizedText.split("\\s+");

        Map<String, Integer> langScores = new HashMap<>(Map.of("pt", 0, "en", 0, "es", 0));

        for (String word : words) {
            for (Map.Entry<String, Set<String>> entry : STOP_WORDS_BY_LANG.entrySet()) {
                if (entry.getValue().contains(word)) {
                    langScores.put(entry.getKey(), langScores.get(entry.getKey()) + 1);
                }
            }
        }

        return langScores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .filter(e -> e.getValue() > 0)
                .map(Map.Entry::getKey)
                .orElse("pt");
    }

    public List<String> tokenize(String text, String lang) {
        Set<String> stopWords = STOP_WORDS_BY_LANG.getOrDefault(lang, STOP_WORDS_BY_LANG.get("pt"));
        return Arrays.stream(text.split("\\s+"))
                .filter(token -> token.length() >= 2)
                .filter(token -> !stopWords.contains(token))
                .collect(Collectors.toList());
    }

    public String getFallbackMessage(String lang) {
        return switch (lang.toLowerCase()) {
            case "en" -> "Sorry, I didn't quite understand. If you need help, type 'agent' to speak with support.";
            case "es" -> "Lo siento, no entendí bien. Si necesita ayuda, escriba 'agente' para hablar con soporte.";
            default -> "Desculpe, não consegui compreender exatamente. Se precisar de ajuda, digite 'atendente' para falar com o suporte.";
        };
    }
}