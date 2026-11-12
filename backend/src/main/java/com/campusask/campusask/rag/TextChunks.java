package com.campusask.campusask.rag;

import java.util.*;

/** Deterministic overlapping chunks; offsets refer to normalized source text. */
public final class TextChunks {
    private TextChunks() {}
    public static List<String> split(String text) {
        text = text.replace("\r\n", "\n").replace('\u0000', ' ').trim();
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < text.length();) {
            int end = Math.min(start + 800, text.length());
            if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) end--;
            chunks.add(text.substring(start, end));
            if (end == text.length()) break;
            start = end - 120;
            if (Character.isLowSurrogate(text.charAt(start))) start++;
        }
        return chunks;
    }
    public static Set<String> terms(String text) {
        Set<String> result = new HashSet<>();
        String normalized = text.toLowerCase(Locale.ROOT);
        for (String word : normalized.split("[^\\p{L}\\p{N}]+")) {
            if (!word.isBlank()) result.add(word);
            int[] cps = word.codePoints().toArray();
            for (int i = 0; i + 1 < cps.length; i++)
                result.add(new String(cps, i, 2));
        }
        return result;
    }
    public static double cosine(double[] a, double[] b) {
        if (a.length == 0 || a.length != b.length) return 0;
        double dot = 0, aa = 0, bb = 0;
        for (int i = 0; i < a.length; i++) { dot += a[i]*b[i]; aa += a[i]*a[i]; bb += b[i]*b[i]; }
        return aa == 0 || bb == 0 ? 0 : dot / Math.sqrt(aa * bb);
    }
}
