package io.aurora.retrieval;

/**
 * Text + image embedding boundary. The local implementation is deterministic
 * (hashing n-grams / byte histogram) so tests and demos are reproducible.
 */
public interface EmbeddingPort {

    int dimensions();

    float[] embedText(String text);

    float[] embedImage(byte[] image, String caption, String fileName);

    String provider();
}
