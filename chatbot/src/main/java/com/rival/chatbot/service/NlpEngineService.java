package com.rival.chatbot.service;

import com.rival.chatbot.domain.NlpIntentEntity;
import com.rival.chatbot.repository.NlpIntentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class NlpEngineService {

    private static final Logger log = LoggerFactory.getLogger(NlpEngineService.class);
    private final NlpIntentRepository intentRepository;

    public NlpEngineService(NlpIntentRepository intentRepository) {
        this.intentRepository = intentRepository;
    }

    @Cacheable(value = "intents")
    public List<NlpIntentEntity> getAllIntentsCached() {
        log.info("Buscando intenções do banco de dados (Cache Miss)...");
        return intentRepository.findAll();
    }

    @CacheEvict(value = "intents", allEntries = true)
    public void clearIntentsCache() {
        log.info("Limpando cache de intenções NLP...");
    }

    public String processAndMatch(String userMessage) {
        String cleanMessage = normalizeText(userMessage);
        String[] tokens = cleanMessage.split("\\s+");

        List<NlpIntentEntity> allIntents = getAllIntentsCached();
        NlpIntentEntity bestMatch = null;
        double highestScore = 0.0;

        for (NlpIntentEntity intent : allIntents) {
            double currentScore = 0.0;

            for (String keyword : intent.getKeywords()) {
                String cleanKeyword = normalizeText(keyword);

                if (cleanMessage.contains(cleanKeyword)) {
                    currentScore += 3.0;
                } else {
                    for (String token : tokens) {
                        int distance = calculateLevenshteinDistance(token, cleanKeyword);
                        if (distance <= 2 && token.length() >= 4) {
                            currentScore += 1.5;
                        }
                    }
                }
            }

            if (currentScore > highestScore) {
                highestScore = currentScore;
                bestMatch = intent;
            }
        }

        if (bestMatch != null && highestScore >= 1.5 && !bestMatch.getResponses().isEmpty()) {
            List<String> responses = bestMatch.getResponses();
            return responses.get(new Random().nextInt(responses.size()));
        }

        return "Desculpe, não consegui compreender exatamente. Se precisar de ajuda, digite 'atendente' para falar com o suporte.";
    }

    private int calculateLevenshteinDistance(String lhs, String rhs) {
        int len0 = lhs.length() + 1;
        int len1 = rhs.length() + 1;

        int[] cost = new int[len0];
        int[] newcost = new int[len0];

        for (int i = 0; i < len0; i++) cost[i] = i;

        for (int j = 1; j < len1; j++) {
            newcost[0] = j;

            for (int i = 1; i < len0; i++) {
                int match = (lhs.charAt(i - 1) == rhs.charAt(j - 1)) ? 0 : 1;
                int cost_replace = cost[i - 1] + match;
                int cost_insert = cost[i] + 1;
                int cost_delete = newcost[i - 1] + 1;

                newcost[i] = Math.min(Math.min(cost_insert, cost_delete), cost_replace);
            }

            int[] swap = cost;
            cost = newcost;
            newcost = swap;
        }

        return cost[len0 - 1];
    }

    private String normalizeText(String text) {
        if (text == null) return "";
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(normalized).replaceAll("").toLowerCase().trim();
    }
}