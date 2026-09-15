package com.rival.chatbot.service.ai;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GenerativeTokenizer {

    private final Map<String, Integer> tokenToId = new HashMap<>();
    private final Map<Integer, String> idToToken = new HashMap<>();

    public GenerativeTokenizer() {
        buildVocabulary();
    }

    private void buildVocabulary() {
        List<String> tokens = List.of(
                "<PAD>", "<UNK>", "<BOS>", "<EOS>", "crie", "um", "site", "para", "empresa",
                "<!DOCTYPE", "html>", "<head>", "</head>", "<body>", "</body>", "<h1>", "</h1>",
                "<style>", "</style>", "div", "class=", "background:", "#1e293b;", "color:", "white;",
                "display:", "flex;", "font-family:", "sans-serif;", "padding:", "10px;"
        );

        for (int i = 0; i < tokens.size(); i++) {
            tokenToId.put(tokens.get(i), i);
            idToToken.put(i, tokens.get(i));
        }
    }

    public List<Integer> encode(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String[] words = text.toLowerCase().split("\\s+");
        List<Integer> ids = new ArrayList<>();
        for (String word : words) {
            ids.add(tokenToId.getOrDefault(word, 1)); // 1 = <UNK>
        }
        return ids;
    }

    public String decode(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int id : ids) {
            String token = idToToken.getOrDefault(id, "");
            if (!token.startsWith("<")) {
                sb.append(" ");
            }
            sb.append(token);
        }
        return sb.toString().trim();
    }

    public int getVocabSize() {
        return idToToken.size();
    }
}