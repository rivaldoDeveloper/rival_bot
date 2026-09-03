package com.rival.chatbot.util;

import java.text.Normalizer;
import java.util.regex.Pattern;

public final class TextNormalizerUtil {

    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private TextNormalizerUtil() {}

    /**
     * Remove acentos, caracteres especiais e trata ruídos de repetição (ex: "aaaaaaaaaatarquitetura" -> "arquitetura")
     */
    public static String cleanAndDeduplicate(String text) {
        if (text == null) return "";

        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        String clean = DIACRITICS_PATTERN.matcher(normalized).replaceAll("").toLowerCase().trim();

        // Reduz 3 ou mais caracteres repetidos seguidos para apenas 1
        clean = clean.replaceAll("(.)\\1{2,}", "$1");

        // Mantém letras unicode e números
        return clean.replaceAll("[^\\p{L}0-9\\s]", " ");
    }

    /**
     * Calcula a similaridade entre duas strings usando a Distância de Levenshtein
     */
    public static double calculateSimilarity(String s1, String s2) {
        int distance = calculateLevenshteinDistance(s1, s2);
        int maxLength = Math.max(s1.length(), s2.length());
        if (maxLength == 0) return 1.0;
        return 1.0 - ((double) distance / maxLength);
    }

    private static int calculateLevenshteinDistance(String lhs, String rhs) {
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
}