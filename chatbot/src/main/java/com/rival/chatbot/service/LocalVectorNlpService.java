package com.rival.chatbot.service;

import com.rival.chatbot.domain.NlpIntentEntity;
import com.rival.chatbot.repository.NlpIntentRepository;
import com.rival.chatbot.util.TextNormalizerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LocalVectorNlpService {

    private static final Logger log = LoggerFactory.getLogger(LocalVectorNlpService.class);
    private final NlpIntentRepository intentRepository;
    private final Map<UUID, String> lastResponseBySession = new ConcurrentHashMap<>();

    public LocalVectorNlpService(NlpIntentRepository intentRepository) {
        this.intentRepository = intentRepository;
    }

    @Cacheable(value = "intents")
    public List<NlpIntentEntity> getAllIntentsCached() {
        log.info("Carregando base de conhecimento do PostgreSQL Neon...");
        return intentRepository.findAll();
    }

    @CacheEvict(value = "intents", allEntries = true)
    public void clearIntentsCache() {
        log.info("Atualizando cache de PNL...");
    }

    public String processAndMatch(UUID sessionId, String userMessage) {
        if (userMessage == null || userMessage.isBlank()) return "";

        List<NlpIntentEntity> intents = getAllIntentsCached();
        if (intents.isEmpty()) return "Ainda não fui treinado.";

        String cleanedInput = TextNormalizerUtil.cleanAndDeduplicate(userMessage);
        float[] userInputVector = generateTextVector(cleanedInput);

        NlpIntentEntity bestMatch = null;
        double highestSimilarity = -1.0;

        for (NlpIntentEntity intent : intents) {
            float[] nameVector = generateTextVector(TextNormalizerUtil.cleanAndDeduplicate(intent.getName()));
            double maxScore = calculateCosineSimilarity(userInputVector, nameVector);

            if (intent.getKeywords() != null) {
                for (String keyword : intent.getKeywords()) {
                    String cleanKw = TextNormalizerUtil.cleanAndDeduplicate(keyword);
                    if (cleanedInput.contains(cleanKw)) {
                        maxScore = Math.max(maxScore, 0.95);
                    } else {
                        float[] kwVector = generateTextVector(cleanKw);
                        maxScore = Math.max(maxScore, calculateCosineSimilarity(userInputVector, kwVector));
                    }
                }
            }

            if (maxScore > highestSimilarity) {
                highestSimilarity = maxScore;
                bestMatch = intent;
            }
        }

        // Limiar de confiança da matemática vetorial (55%)
        if (bestMatch != null && highestSimilarity >= 0.55 && !bestMatch.getResponses().isEmpty()) {
            List<String> responses = bestMatch.getResponses();
            String response = responses.get(new Random().nextInt(responses.size()));
            if (sessionId != null) lastResponseBySession.put(sessionId, response);
            return response;
        }

        return "Desculpe, não localizei essa intenção na base.";
    }

    private float[] generateTextVector(String text) {
        float[] vector = new float[128];
        String padded = "  " + text + "  ";
        for (int i = 0; i < padded.length() - 2; i++) {
            int hash = Math.abs(padded.substring(i, i + 3).hashCode()) % 128;
            vector[hash] += 1.0f;
        }
        float norm = 0.0f;
        for (float v : vector) norm += v * v;
        norm = (float) Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) vector[i] /= norm;
        }
        return vector;
    }

    private double calculateCosineSimilarity(float[] vA, float[] vB) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < vA.length; i++) {
            dot += vA[i] * vB[i];
            normA += vA[i] * vA[i];
            normB += vB[i] * vB[i];
        }
        return (normA == 0 || normB == 0) ? 0 : dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}