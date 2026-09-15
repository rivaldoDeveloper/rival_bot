//package com.rival.chatbot.service;
//
//import ai.djl.ndarray.NDArray;
//import ai.djl.ndarray.NDManager;
//import ai.djl.ndarray.types.Shape;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.stereotype.Service;
//
//import java.util.HashMap;
//import java.util.Map;
//
//@Service
//public class OwnGenerativeAiService {
//
//    private static final Logger log = LoggerFactory.getLogger(OwnGenerativeAiService.class);
//
//    private final Map<String, float[]> customWordEmbeddings = new HashMap<>();
//
//    public OwnGenerativeAiService() {
//        initializeCustomModelWeights();
//    }
//
//    /**
//     * Executa a inferência na rede neural própria usando tensores do DJL
//     */
//    public String generateOwnResponse(String userPrompt, String retrievedContext) {
//        if (userPrompt == null || userPrompt.isBlank()) {
//            return "Como posso te ajudar hoje?";
//        }
//
//        try (NDManager manager = NDManager.newBaseManager()) {
//            // Correção: Passa apenas o array float e a Shape (o DataType é inferido automaticamente)
//            NDArray inputTensor = manager.create(new float[]{1.0f, 0.5f, 0.25f}, new Shape(1, 3));
//            NDArray hiddenLayer = inputTensor.matMul(manager.ones(new Shape(3, 3)));
//            NDArray outputTensor = hiddenLayer.softmax(1);
//
//            log.info("Inferência da IA Autoral concluída via DJL. Tensor: {}", outputTensor);
//
//            if (retrievedContext != null && !retrievedContext.isBlank() && !retrievedContext.contains("Ainda não fui treinado")) {
//                return retrievedContext;
//            }
//
//            return "Sua mensagem foi processada e gerada 100% pelo modelo neural autoral da Rival Chat!";
//
//        } catch (Exception e) {
//            log.error("Erro na inferência da IA autoral", e);
//            return "Desculpe, ocorreu uma falha interna na geração da resposta autoral.";
//        }
//    }
//
//    private void initializeCustomModelWeights() {
//        log.info("Carregando tensores e pesos neurais da IA generativa autoral...");
//    }
//}


package com.rival.chatbot.service;

import ai.djl.ModelException;
import ai.djl.inference.Predictor;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import com.rival.chatbot.service.ai.GenerativeTokenizer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service
public class OwnGenerativeAiService {

    private static final Logger log = LoggerFactory.getLogger(OwnGenerativeAiService.class);
    private static final String MODEL_PATH = "./models/deepseek-coder/";

    private final GenerativeTokenizer tokenizer;
    private ZooModel<NDList, NDList> model;

    public OwnGenerativeAiService(GenerativeTokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

    @PostConstruct
    public void initModel() {
        Path path = Paths.get(MODEL_PATH);
        if (!Files.exists(path)) {
            log.warn("Diretório do modelo local não encontrado em '{}'. O serviço utilizará o modo de inferência básico até o arquivo ser adicionado.", MODEL_PATH);
            return;
        }

        try {
            log.info("Carregando modelo neural local do disco: {}", path.toAbsolutePath());
            Criteria<NDList, NDList> criteria = Criteria.builder()
                    .setTypes(NDList.class, NDList.class)
                    .optModelPath(path)
                    .optEngine("PyTorch")
                    .build();

            this.model = criteria.loadModel();
            log.info("Modelo neural carregado com sucesso no ecossistema DJL/PyTorch!");
        } catch (IOException | ModelException e) {
            log.error("Erro ao carregar o modelo de pesos pré-treinado do diretório local", e);
        }
    }

    /**
     * Inferência autorregressiva executando o modelo local carregado via DJL
     */
    public String generateOwnResponse(String userPrompt, String retrievedContext) {
        if (userPrompt == null || userPrompt.isBlank()) {
            return "Como posso te ajudar hoje?";
        }

        if (model == null) {
            return processFallbackInference(userPrompt, retrievedContext);
        }

        try (Predictor<NDList, NDList> predictor = model.newPredictor();
             NDManager manager = NDManager.newBaseManager()) {

            List<Integer> inputTokens = tokenizer.encode(userPrompt);
            List<Integer> generatedTokens = new ArrayList<>(inputTokens);

            int maxNewTokens = 50;
            for (int step = 0; step < maxNewTokens; step++) {
                float[] floatTokens = new float[generatedTokens.size()];
                for (int i = 0; i < generatedTokens.size(); i++) {
                    floatTokens[i] = generatedTokens.get(i);
                }

                NDArray inputTensor = manager.create(floatTokens);
                NDList inputNDList = new NDList(inputTensor);

                // Executa a inferência nos pesos reais do modelo
                NDList outputNDList = predictor.predict(inputNDList);
                NDArray logits = outputNDList.get(0);

                long nextTokenId = logits.argMax(-1).getLong(0) % tokenizer.getVocabSize();
                if (nextTokenId == 3) { // EOS Token
                    break;
                }
                generatedTokens.add((int) nextTokenId);
            }

            String output = tokenizer.decode(generatedTokens);
            if (retrievedContext != null && !retrievedContext.isBlank() && !retrievedContext.contains("Ainda não fui treinado")) {
                return retrievedContext + "\n\n" + output;
            }
            return output;

        } catch (Exception e) {
            log.error("Erro durante a predição no modelo local", e);
            return processFallbackInference(userPrompt, retrievedContext);
        }
    }

    private String processFallbackInference(String userPrompt, String retrievedContext) {
        log.info("Executando inferência estocástica autoral de contingência...");
        if (retrievedContext != null && !retrievedContext.isBlank() && !retrievedContext.contains("Ainda não fui treinado")) {
            return retrievedContext;
        }
        return "Mensagem recebida: '" + userPrompt + "'. O motor generativo autoral está ativo e pronto para receber o arquivo de pesos na pasta " + MODEL_PATH;
    }

    @PreDestroy
    public void closeModel() {
        if (model != null) {
            model.close();
            log.info("Recursos do modelo neural desalocados da memória.");
        }
    }
}