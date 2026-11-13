package io.aurora.retrieval;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds the vector payload from MySQL documents. Idempotent: every startup
 * upserts the current ACL and embedding, which also repairs partial indexes.
 */
@Component
@Order(15)
public class VectorIndexer implements ApplicationRunner {

    private final DocumentRepository documents;
    private final EmbeddingPort embedding;
    private final VectorStorePort vectorStore;

    public VectorIndexer(DocumentRepository documents, EmbeddingPort embedding,
                         VectorStorePort vectorStore) {
        this.documents = documents;
        this.embedding = embedding;
        this.vectorStore = vectorStore;
    }

    @Override
    public void run(ApplicationArguments args) {
        reindexAll();
    }

    public synchronized int reindexAll() {
        List<DocumentRecord> all = documents.findAll();
        for (DocumentRecord doc : all) {
            vectorStore.upsert(toRecord(doc));
        }
        return all.size();
    }

    public void reindex(DocumentRecord doc) {
        vectorStore.upsert(toRecord(doc));
    }

    private VectorRecord toRecord(DocumentRecord doc) {
        String text = String.join("\n", doc.title(), doc.summary(), doc.content());
        return new VectorRecord(
                "doc-" + doc.id(), 0L, doc.objectType(), doc.objectId(), doc.id(),
                doc.title(), doc.summary(), doc.page(), doc.paragraph(), doc.ownerId(),
                doc.visibility(), doc.visibleRoles(), doc.visibleDepts(), doc.aclVersion(),
                doc.requiredPermission(), doc.fileName(), embedding.embedText(text));
    }
}
