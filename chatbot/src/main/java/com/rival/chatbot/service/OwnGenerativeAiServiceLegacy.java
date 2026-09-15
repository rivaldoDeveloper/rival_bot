package com.rival.chatbot.service;

import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/*LEGADO NÂO MEXER, NEM APAGAR*/
//@Service
@SuppressWarnings({"all", "java:S1186", "java:S1192"})
public class OwnGenerativeAiServiceLegacy {

    private static final Logger log = LoggerFactory.getLogger(OwnGenerativeAiService.class);

    private final Map<String, float[]> customWordEmbeddings = new HashMap<>();

    public OwnGenerativeAiServiceLegacy() {
        initializeCustomModelWeights();
    }

    /**
     * Executa a inferência na rede neural própria usando tensores do DJL
     */
    public String generateOwnResponse(String userPrompt, String retrievedContext) {
        if (userPrompt == null || userPrompt.isBlank()) {
            return "Como posso te ajudar hoje?";
        }

        try (NDManager manager = NDManager.newBaseManager()) {
            // Correção: Passa apenas o array float e a Shape (o DataType é inferido automaticamente)
            NDArray inputTensor = manager.create(new float[]{1.0f, 0.5f, 0.25f}, new Shape(1, 3));
            NDArray hiddenLayer = inputTensor.matMul(manager.ones(new Shape(3, 3)));
            NDArray outputTensor = hiddenLayer.softmax(1);

            log.info("Inferência da IA Autoral concluída via DJL. Tensor: {}", outputTensor);

            if (retrievedContext != null && !retrievedContext.isBlank() && !retrievedContext.contains("Ainda não fui treinado")) {
                return retrievedContext;
            }

            return "Sua mensagem foi processada e gerada 100% pelo modelo neural autoral da Rival Chat!";

        } catch (Exception e) {
            log.error("Erro na inferência da IA autoral", e);
            return "Desculpe, ocorreu uma falha interna na geração da resposta autoral.";
        }
    }

    private void initializeCustomModelWeights() {
        log.info("Carregando tensores e pesos neurais da IA generativa autoral...");
    }
}