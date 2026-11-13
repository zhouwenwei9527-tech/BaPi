package io.aurora.retrieval;

import java.util.List;

/**
 * Swappable vector store. pgvector is the local adapter; Qdrant can implement
 * the same port by translating the Principal into a payload filter.
 */
public interface VectorStorePort {

    List<VectorHit> search(VectorQuery query);

    void upsert(VectorRecord record);

    void deleteByDoc(String docId);

    long count();
}
