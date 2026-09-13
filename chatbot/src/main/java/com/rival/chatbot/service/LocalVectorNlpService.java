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

    // Mapa em memória thread-safe para armazenar a última resposta enviada para cada sessão (sessionId -> última resposta)
    private final Map<UUID, String> lastResponseBySession = new ConcurrentHashMap<>();

    public LocalVectorNlpService(NlpIntentRepository intentRepository) {
        this.intentRepository = intentRepository;
    }

    /**
     * Busca todas as intenções salvas no PostgreSQL e armazena o resultado no Cache do Spring.
     * Na primeira requisição consulta o banco (Cache Miss). Nas requisições seguintes,
     * retorna direto da memória em 0ms sem sobrecarregar o banco.
     */
    @Cacheable(value = "intents")
    public List<NlpIntentEntity> getAllIntentsCached() {
        log.info("Buscando intenções no PostgreSQL para alimentar o Cache...");
        return intentRepository.findAll();
    }

    /**
     * Limpa o cache para que novas intenções cadastradas sejam recarregadas imediatamente do banco.
     */
    @CacheEvict(value = "intents", allEntries = true)
    public void clearIntentsCache() {
        log.info("Limpando cache de intenções NLP...");
    }

    /**
     * Sobrecarga de conveniência para chamadas sem ID de sessão explícito.
     */
    public String processAndMatch(String userMessage) {
        return processAndMatch(null, userMessage);
    }

    /**
     * Processa a mensagem digitada pelo usuário, executa a comparação vetorial e
     * seleciona a resposta ideal garantindo que a mesma frase não seja repetida em sequência.
     */
    public String processAndMatch(UUID sessionId, String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return "Como posso te ajudar hoje?";
        }

        // 1. Obtém a lista de intenções (utilizando o Cache em memória)
        List<NlpIntentEntity> intents = getAllIntentsCached();
        if (intents.isEmpty()) {
            return "Ainda não fui treinado com nenhuma informação sobre a empresa.";
        }

        // 2. Normaliza a mensagem do usuário (remove acentos, pontuações e caracteres repetidos)
        String cleanedInput = TextNormalizerUtil.cleanAndDeduplicate(userMessage);
        float[] userInputVector = generateTextVector(cleanedInput);

        NlpIntentEntity bestMatch = null;
        double highestSimilarity = -1.0;

        // 3. Itera sobre cada intenção cadastrada e compara o vetor da mensagem
        for (NlpIntentEntity intent : intents) {
            // Avalia similaridade semântica com o NOME da intenção
            float[] nameVector = generateTextVector(TextNormalizerUtil.cleanAndDeduplicate(intent.getName()));
            double maxIntentScore = calculateCosineSimilarity(userInputVector, nameVector);

            // Avalia similaridade semântica contra TODAS as palavras-chave da intenção
            if (intent.getKeywords() != null) {
                for (String keyword : intent.getKeywords()) {
                    String cleanedKeyword = TextNormalizerUtil.cleanAndDeduplicate(keyword);

                    // Match exato ou contido de palavra-chave atribui pontuação alta (0.90)
                    if (cleanedInput.equals(cleanedKeyword) || cleanedInput.contains(cleanedKeyword)) {
                        maxIntentScore = Math.max(maxIntentScore, 0.90);
                    } else {
                        float[] keywordVector = generateTextVector(cleanedKeyword);
                        double kwSimilarity = calculateCosineSimilarity(userInputVector, keywordVector);
                        if (kwSimilarity > maxIntentScore) {
                            maxIntentScore = kwSimilarity;
                        }
                    }
                }
            }

            log.info("Similaridade Semântica com [{}] -> {}%", intent.getName(), String.format("%.2f", maxIntentScore * 100));

            if (maxIntentScore > highestSimilarity) {
                highestSimilarity = maxIntentScore;
                bestMatch = intent;
            }
        }

        // 4. Valida o limiar de confiança semântico (0.45 = 45% de certeza)
        if (bestMatch != null && highestSimilarity >= 0.45 && !bestMatch.getResponses().isEmpty()) {
            List<String> responses = bestMatch.getResponses();

            // Seleciona uma resposta filtrando a que foi enviada recentemente para esta sessão
            String chosenResponse = selectNonRepeatingResponse(sessionId, responses);

            // Salva a resposta escolhida no histórico da sessão do usuário
            if (sessionId != null) {
                lastResponseBySession.put(sessionId, chosenResponse);
            }

            return chosenResponse;
        }

        // Mensagem de fallback caso a similaridade seja inferior a 45%
        return "Desculpe, não consegui entender exatamente sua solicitação. Pode reformular?";
    }

    /**
     * Filtra a lista de respostas de uma intenção para evitar enviar duas vezes seguidas
     * o mesmo texto para o mesmo usuário na mesma conversa.
     */
    private String selectNonRepeatingResponse(UUID sessionId, List<String> responses) {
        // Se houver apenas uma resposta ou a sessão não for informada, retorna a única disponível
        if (responses.size() == 1 || sessionId == null) {
            return responses.get(0);
        }

        String lastResponse = lastResponseBySession.get(sessionId);
        List<String> filteredResponses = new ArrayList<>();

        // Adiciona à lista de candidatas apenas as respostas diferentes da última enviada
        for (String resp : responses) {
            if (!resp.equalsIgnoreCase(lastResponse)) {
                filteredResponses.add(resp);
            }
        }

        // Se por algum motivo a lista filtrada ficar vazia, sorteia da lista original
        if (filteredResponses.isEmpty()) {
            return responses.get(new Random().nextInt(responses.size()));
        }

        // Sorteia de forma aleatória entre as respostas não repetidas
        return filteredResponses.get(new Random().nextInt(filteredResponses.size()));
    }

    /**
     * Algoritmo nativo de Embedding (Char N-Gram Hash Vectorizer).
     * Transforma qualquer texto em um vetor matemático de 128 dimensões.
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

        // Normalização L2 do vetor para manter a escala unitária
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
     * Calcula a Similaridade de Cosseno entre dois vetores (Grau de Proximidade Semântica de -1 a 1).
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