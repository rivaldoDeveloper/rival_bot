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
import java.util.regex.Pattern;

@Service
public class LocalVectorNlpService {

    private static final Logger log = LoggerFactory.getLogger(LocalVectorNlpService.class);
    private final NlpIntentRepository intentRepository;

    // Guarda a última resposta e a última intenção de cada sessão para manter contexto conversacional
    private final Map<UUID, String> lastResponseBySession = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastIntentBySession = new ConcurrentHashMap<>();

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "o", "as", "os", "um", "uma", "de", "do", "da", "dos", "das", "em", "no", "na",
            "por", "para", "com", "que", "se", "ou", "e", "eu", "voce", "voces", "me", "meu", "minha",
            "the", "is", "are", "to", "in", "for", "of", "and", "el", "la", "los", "las", "un", "una"
    );

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
        return processAndMatch(sessionId, null, userMessage);
    }

    public String processAndMatch(UUID sessionId, UUID tenantId, String userMessage) {
        if (userMessage == null || userMessage.isBlank()) return "";

        List<NlpIntentEntity> intents = getAllIntentsCached().stream()
                .filter(i -> tenantId == null || tenantId.equals(i.getTenantId()))
                .toList();

        if (intents.isEmpty()) return "__UNTRAINED__";

        String cleanedInput = TextNormalizerUtil.cleanAndDeduplicate(userMessage);
        if (cleanedInput.isBlank()) return "__NO_MATCH__";

        List<String> inputTokens = extractMeaningfulTokens(cleanedInput);
        float[] userInputVector = generateTextVector(cleanedInput);

        List<ScoredIntent> scoredIntents = new ArrayList<>();

        for (NlpIntentEntity intent : intents) {
            double score = evaluateDeepSemanticScore(cleanedInput, inputTokens, userInputVector, intent, sessionId);
            if (score > 0) {
                scoredIntents.add(new ScoredIntent(intent, score));
            }
        }

        // Ordena da maior pontuação semântica para a menor
        scoredIntents.sort((a, b) -> Double.compare(b.score(), a.score()));

        if (scoredIntents.isEmpty() || scoredIntents.get(0).score() < 0.68) {
            log.info("NLP Semântico: Nenhuma intenção atingiu confiança mínima para '{}' (Tenant: {})", userMessage, tenantId);
            return "__NO_MATCH__";
        }

        ScoredIntent top1 = scoredIntents.get(0);
        log.info("NLP Top Match: '{}' (Score: {}) para Tenant: {}", top1.intent().getName(), String.format("%.2f", top1.score()), tenantId);

        if (sessionId != null) {
            lastIntentBySession.put(sessionId, top1.intent().getId());
        }

        String primaryResponse = pickSmartResponse(sessionId, top1.intent());

        // INTELIGÊNCIA MULTI-INTENÇÃO (Igual ao Gemini):
        // Se o utilizador fez duas perguntas na mesma frase (ex: Saudação + Preço, ou Preço + Horário)
        // e a 2ª intenção também teve score altíssimo (>= 0.82) com gatilhos distintos, combina as duas respostas!
        if (scoredIntents.size() > 1 && inputTokens.size() >= 3) {
            ScoredIntent top2 = scoredIntents.get(1);
            if (top2.score() >= 0.84 && (top1.score() - top2.score()) <= 0.18) {
                String secondaryResponse = pickSmartResponse(sessionId, top2.intent());
                if (!secondaryResponse.equalsIgnoreCase(primaryResponse)) {
                    return primaryResponse + "||NLP_MULTI||" + secondaryResponse;
                }
            }
        }

        return primaryResponse;
    }

    /**
     * Calcula a pontuação híbrida combinando:
     * 1. Match exato de frase/expressão
     * 2. Cobertura de tokens significativos (permite repetir palavras-chave entre intenções, vencendo a mais específica!)
     * 3. Tolerância a erros de digitação (Levenshtein Fuzzy Match)
     * 4. Similaridade de Cosseno por N-Gramas
     */
    private double evaluateDeepSemanticScore(String cleanedInput,
                                             List<String> inputTokens,
                                             float[] userInputVector,
                                             NlpIntentEntity intent,
                                             UUID sessionId) {
        String cleanIntentName = TextNormalizerUtil.cleanAndDeduplicate(intent.getName().replace("_", " "));
        float[] nameVector = generateTextVector(cleanIntentName);
        double bestKeywordScore = calculateCosineSimilarity(userInputVector, nameVector) * 0.75;

        if (intent.getKeywords() == null || intent.getKeywords().isEmpty()) {
            return bestKeywordScore;
        }

        for (String keyword : intent.getKeywords()) {
            String cleanKw = TextNormalizerUtil.cleanAndDeduplicate(keyword);
            if (cleanKw.isBlank()) continue;

            List<String> kwTokens = extractMeaningfulTokens(cleanKw);
            double currentScore = 0.0;

            // 1. Frase idêntica
            if (cleanedInput.equals(cleanKw)) {
                currentScore = 1.0 + (kwTokens.size() * 0.05); // Bônus por especificidade da frase
            }
            // 2. A frase do cliente contém a expressão inteira do gatilho como palavras completas
            else if (cleanedInput.matches(".*\\b" + Pattern.quote(cleanKw) + "\\b.*")) {
                if (kwTokens.size() >= 2) {
                    // Gatilho composto de várias palavras (alta precisão contextual)
                    currentScore = 0.92 + Math.min(kwTokens.size() * 0.03, 0.12);
                } else {
                    // Gatilho de 1 palavra só: pontua bem se a frase for curta, ou exige apoio contextual se for longa
                    currentScore = (inputTokens.size() <= 4) ? 0.86 : 0.72;
                }
            }
            // 3. Análise de cobertura de palavras + Tolerância a erros de digitação (Fuzzy Levenshtein)
            else if (!kwTokens.isEmpty() && !inputTokens.isEmpty()) {
                int matchedTokens = 0;
                double fuzzySum = 0.0;

                for (String kwToken : kwTokens) {
                    double bestTokenSim = 0.0;
                    for (String inToken : inputTokens) {
                        if (inToken.equals(kwToken)) {
                            bestTokenSim = 1.0;
                            break;
                        } else if (inToken.length() >= 4 && kwToken.length() >= 4) {
                            double sim = TextNormalizerUtil.calculateSimilarity(inToken, kwToken);
                            if (sim >= 0.80) { // Aceita pequenos erros de digitação (ex: "horariu" -> "horario")
                                bestTokenSim = Math.max(bestTokenSim, sim);
                            }
                        }
                    }
                    if (bestTokenSim >= 0.80) {
                        matchedTokens++;
                        fuzzySum += bestTokenSim;
                    }
                }

                double kwCoverage = (double) matchedTokens / kwTokens.size();
                double inputCoverage = (double) matchedTokens / inputTokens.size();

                if (kwCoverage >= 0.75) {
                    double avgFuzzy = fuzzySum / matchedTokens;
                    // Combina cobertura do gatilho + cobertura da pergunta + bônus de especificidade
                    currentScore = (kwCoverage * 0.65 + inputCoverage * 0.25) * avgFuzzy + (matchedTokens * 0.04);
                } else {
                    // 4. Fallback Vetorial de N-Gramas
                    float[] kwVector = generateTextVector(cleanKw);
                    currentScore = calculateCosineSimilarity(userInputVector, kwVector) * 0.85;
                }
            }

            if (currentScore > bestKeywordScore) {
                bestKeywordScore = currentScore;
            }
        }

        // Bônus sutil de continuidade se o cliente estiver aprofundando o mesmo assunto da mensagem anterior
        if (sessionId != null && intent.getId() != null && intent.getId().equals(lastIntentBySession.get(sessionId))) {
            if (bestKeywordScore >= 0.65 && bestKeywordScore < 0.85) {
                bestKeywordScore += 0.04;
            }
        }

        return bestKeywordScore;
    }

    private List<String> extractMeaningfulTokens(String normalizedText) {
        if (normalizedText == null || normalizedText.isBlank()) return List.of();
        return Arrays.stream(normalizedText.split("\\s+"))
                .filter(t -> t.length() >= 2 && !STOP_WORDS.contains(t))
                .toList();
    }

    /**
     * Escolhe uma resposta da intenção evitando repetir exatamente a mesma frase seguida na sessão.
     */
    private String pickSmartResponse(UUID sessionId, NlpIntentEntity intent) {
        List<String> responses = intent.getResponses();
        if (responses == null || responses.isEmpty()) return "";
        if (responses.size() == 1) {
            String single = responses.get(0);
            if (sessionId != null) lastResponseBySession.put(sessionId, single);
            return single;
        }

        String lastResp = (sessionId != null) ? lastResponseBySession.get(sessionId) : null;
        List<String> candidates = responses.stream()
                .filter(r -> !r.equals(lastResp))
                .toList();

        List<String> pool = candidates.isEmpty() ? responses : candidates;
        String chosen = pool.get(new Random().nextInt(pool.size()));
        if (sessionId != null) {
            lastResponseBySession.put(sessionId, chosen);
        }
        return chosen;
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

    private record ScoredIntent(NlpIntentEntity intent, double score) {}
}