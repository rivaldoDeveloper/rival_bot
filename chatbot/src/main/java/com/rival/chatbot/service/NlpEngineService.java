package com.rival.chatbot.service;

import com.rival.chatbot.domain.NlpIntentEntity;
import com.rival.chatbot.repository.NlpIntentRepository;
import com.rival.chatbot.util.TextNormalizerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/*@Service*/
public class NlpEngineService {

    private static final Logger log = LoggerFactory.getLogger(NlpEngineService.class);
    private final NlpIntentRepository intentRepository;
    private final LanguageDetectorService languageDetectorService;

    public NlpEngineService(NlpIntentRepository intentRepository, LanguageDetectorService languageDetectorService) {
        this.intentRepository = intentRepository;
        this.languageDetectorService = languageDetectorService;
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
        List<NlpIntentEntity> allIntents = getAllIntentsCached();
        if (allIntents.isEmpty()) {
            return "Desculpe, ainda não fui treinado com nenhuma intenção de atendimento.";
        }

        String lang = languageDetectorService.detectLanguage(userMessage);
        String cleanedMsg = TextNormalizerUtil.cleanAndDeduplicate(userMessage);
        List<String> tokens = languageDetectorService.tokenize(cleanedMsg, lang);

        List<NlpIntentEntity> candidates = filterCandidatesByLang(allIntents, lang);

        NlpIntentEntity bestIntent = null;
        double maxScore = 0.0;

        for (NlpIntentEntity intent : candidates) {
            double score = evaluateIntentScore(intent, cleanedMsg, tokens);
            if (score > maxScore) {
                maxScore = score;
                bestIntent = intent;
            }
        }

        log.info("Score NLP: {} | Idioma: [{}] | Mensagem: '{}'", maxScore, lang, cleanedMsg);

        if (bestIntent != null && maxScore >= 1.5 && !bestIntent.getResponses().isEmpty()) {
            List<String> responses = bestIntent.getResponses();
            return responses.get(new Random().nextInt(responses.size()));
        }

        return languageDetectorService.getFallbackMessage(lang);
    }

    private List<NlpIntentEntity> filterCandidatesByLang(List<NlpIntentEntity> intents, String lang) {
        List<NlpIntentEntity> filtered = intents.stream()
                .filter(i -> i.getLanguage() != null && i.getLanguage().equalsIgnoreCase(lang))
                .collect(Collectors.toList());
        return filtered.isEmpty() ? intents : filtered;
    }

    private double evaluateIntentScore(NlpIntentEntity intent, String cleanedMsg, List<String> tokens) {
        double score = 0.0;
        for (String keyword : intent.getKeywords()) {
            String cleanKw = TextNormalizerUtil.cleanAndDeduplicate(keyword);

            if (cleanedMsg.contains(cleanKw) || cleanKw.contains(cleanedMsg)) {
                score += 4.0;
            } else {
                for (String token : tokens) {
                    if (token.contains(cleanKw) || cleanKw.contains(token)) {
                        score += 3.0;
                    } else {
                        double similarity = TextNormalizerUtil.calculateSimilarity(token, cleanKw);
                        if (similarity >= 0.6) {
                            score += (similarity * 2.5);
                        }
                    }
                }
            }
        }
        return score;
    }
}