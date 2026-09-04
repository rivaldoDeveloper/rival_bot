package com.rival.chatbot.service;

import com.rival.chatbot.domain.NlpIntentEntity;
import com.rival.chatbot.repository.NlpIntentRepository;
import com.rival.chatbot.util.TextNormalizerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

@Service
public class LocalVectorNlpService {

    private static final Logger log = LoggerFactory.getLogger(LocalVectorNlpService.class);
    private final NlpIntentRepository intentRepository;

    public LocalVectorNlpService(NlpIntentRepository intentRepository) {
        this.intentRepository = intentRepository;
    }

    /**
     * Busca todas as intenções salvas no PostgreSQL e armazena o resultado na memória Cache do Spring.
     * Na primeira requisição ele consulta o banco (Cache Miss). Nas requisições seguintes,
     * retorna direto da memória em 0ms sem sobrecarregar o banco.
     */
    @Cacheable(value = "intents")
    public List<NlpIntentEntity> getAllIntentsCached() {
        log.info("Buscando intenções no PostgreSQL para alimentar o Cache...");
        return intentRepository.findAll();
    }

    /**
     * Limpa o cache para que novas intenções cadastradas sejam recarregadas imediatamente.
     */
    @CacheEvict(value = "intents", allEntries = true)
    public void clearIntentsCache() {
        log.info("Limpando cache de intenções NLP...");
    }

    /**
     * Processa qualquer frase digitada pelo usuário (mesmo com erros grotescos ou gírias)
     * e encontra a melhor intenção cadastrada usando álgebra vetorial local.
     */
    public String processAndMatch(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return "Como posso te ajudar hoje?";
        }

        // AGORA BUSCA DO MÉTODO COM CACHE
        List<NlpIntentEntity> intents = getAllIntentsCached();
        if (intents.isEmpty()) {
            return "Ainda não fui treinado com nenhuma informação sobre a empresa.";
        }

        String cleanedInput = TextNormalizerUtil.cleanAndDeduplicate(userMessage);
        float[] userInputVector = generateTextVector(cleanedInput);

        NlpIntentEntity bestMatch = null;
        double highestSimilarity = -1.0;

        for (NlpIntentEntity intent : intents) {
            // Gera o vetor para o nome da intenção / contexto cadastrado
            float[] intentVector = generateTextVector(TextNormalizerUtil.cleanAndDeduplicate(intent.getName()));
            double similarity = calculateCosineSimilarity(userInputVector, intentVector);

            log.info("Similaridade Semântica com [{}] -> {}%", intent.getName(), String.format("%.2f", similarity * 100));

            if (similarity > highestSimilarity) {
                highestSimilarity = similarity;
                bestMatch = intent;
            }
        }

        // Limiar de confiança semântico
        if (bestMatch != null && highestSimilarity >= 0.30 && !bestMatch.getResponses().isEmpty()) {
            List<String> responses = bestMatch.getResponses();
            return responses.get(new Random().nextInt(responses.size()));
        }

        return "Desculpe, não consegui entender exatamente sua solicitação. Pode reformular?";
    }

    /**
     * Algoritmo nativo de Embedding (Char N-Gram Hash Vectorizer).
     * Transforma qualquer texto humano em um vetor matemático de 128 dimensões.
     */
    private float[] generateTextVector(String text) {
        float[] vector = new float[128];
        if (text == null || text.isBlank()) return vector;

        String padded = "  " + text + "  ";
        for (int i = 0; i < padded.length() - 2; i++) {
            String trigram = padded.substring(i, i + 3);
            int hash = Math.abs(trigram.hashCode()) % 128;
            vector[hash] += 1.0f;
        }

        // Normalização L2 do vetor
        float sum = 0.0f;
        for (float v : vector) sum += v * v;
        float norm = (float) Math.sqrt(sum);
        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= norm;
            }
        }
        return vector;
    }

    /**
     * Calcula o Cosseno do Ângulo entre dois Vetores (Grau de Proximidade Semântica)
     */
    private double calculateCosineSimilarity(float[] vectorA, float[] vectorB) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }
        if (normA == 0 || normB == 0) return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}