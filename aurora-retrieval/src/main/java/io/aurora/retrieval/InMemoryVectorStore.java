package io.aurora.retrieval;

import io.aurora.rbac.Principal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Deterministic in-process vector store used by tests and offline mode.
 * Filtering happens inside search, never after recall (INV-7).
 */
@Component
@ConditionalOnProperty(name = "aurora.retrieval.store", havingValue = "memory", matchIfMissing = true)
public class InMemoryVectorStore implements VectorStorePort {

    private final List<VectorRecord> records = new CopyOnWriteArrayList<>();

    @Override
    public List<VectorHit> search(VectorQuery query) {
        return records.stream()
                .filter(r -> r.tenantId() == query.tenantId())
                .filter(r -> allowed(r, query.principal()))
                .map(r -> new VectorHit(cosine(query.embedding(), r.embedding()), r))
                .sorted(Comparator.comparingDouble(VectorHit::score).reversed())
                .limit(Math.max(1, query.limit()))
                .toList();
    }

    @Override
    public void upsert(VectorRecord record) {
        records.removeIf(r -> r.id().equals(record.id()));
        records.add(record);
    }

    @Override
    public void deleteByDoc(String docId) {
        records.removeIf(r -> r.docId().equals(docId));
    }

    @Override
    public long count() {
        return records.size();
    }

    private boolean allowed(VectorRecord record, Principal principal) {
        if (!principal.can(record.requiredPermission())) {
            return false;
        }
        return switch (record.visibility()) {
            case "PUBLIC" -> true;
            case "ROLES" -> record.visibleRoles().stream().anyMatch(principal.roles()::contains);
            case "DEPTS" -> principal.dataScope().isAll()
                    || record.visibleDepts().stream().anyMatch(principal.dataScope().deptIds()::contains);
            case "OWNER" -> record.ownerId() == principal.userId();
            default -> false;
        };
    }

    private double cosine(float[] a, float[] b) {
        int length = Math.min(a.length, b.length);
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
