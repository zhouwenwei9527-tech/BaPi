package io.aurora.retrieval;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 256-dimension local embedding: FNV-1a hashed character n-grams plus word
 * tokens, L2-normalized. Images mix a byte histogram with the caption so the
 * multimodal query still lives in the same space as seeded knowledge.
 *
 * This is a deterministic offline stand-in for a production multimodal model;
 * it is labelled as such in the UI and in docs/adr/0004.
 */
@Component
public class LocalEmbeddingPort implements EmbeddingPort {

    private static final int DIM = 256;

    @Override
    public int dimensions() {
        return DIM;
    }

    @Override
    public String provider() {
        return "local-hash-256";
    }

    @Override
    public float[] embedText(String text) {
        float[] vector = new float[DIM];
        if (text == null || text.isBlank()) {
            vector[0] = 1f;
            return vector;
        }
        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        List<String> tokens = new ArrayList<>();
        for (String word : normalized.split(" ")) {
            if (!word.isBlank()) {
                tokens.add("w:" + word);
            }
        }
        String compact = normalized.replace(" ", "");
        for (int i = 0; i < compact.length(); i++) {
            if (i + 1 < compact.length()) {
                tokens.add("b:" + compact.substring(i, i + 2));
            }
            if (i + 2 < compact.length()) {
                tokens.add("t:" + compact.substring(i, i + 3));
            }
        }
        for (String token : tokens) {
            int hash = fnv1a(token);
            int index = Math.floorMod(hash, DIM);
            float sign = ((hash >>> 8) & 1) == 0 ? 1f : -1f;
            vector[index] += sign;
        }
        return l2(vector);
    }

    @Override
    public float[] embedImage(byte[] image, String caption, String fileName) {
        float[] vector = embedText((caption == null ? "" : caption) + " " + (fileName == null ? "" : fileName));
        if (image != null && image.length > 0) {
            int buckets = 64;
            float[] histogram = new float[DIM];
            int step = Math.max(1, image.length / 4096);
            for (int i = 0; i < image.length; i += step) {
                int b = (image[i] & 0xff) % buckets;
                histogram[b] += 1f;
            }
            for (int i = 0; i < buckets; i++) {
                vector[i] += histogram[i] * 0.02f;
            }
        }
        return l2(vector);
    }

    private int fnv1a(String value) {
        int hash = 0x811c9dc5;
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            hash ^= b & 0xff;
            hash *= 0x01000193;
        }
        return hash;
    }

    private float[] l2(float[] vector) {
        double sum = 0;
        for (float v : vector) {
            sum += v * v;
        }
        double norm = Math.sqrt(sum);
        if (norm == 0) {
            vector[0] = 1f;
            return vector;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / norm);
        }
        return vector;
    }
}
