package io.aurora.retrieval;

import java.util.Set;

public record VectorRecord(
        String id,
        long tenantId,
        String objectType,
        String objectId,
        String docId,
        String title,
        String summary,
        int page,
        int paragraph,
        long ownerId,
        String visibility,
        Set<String> visibleRoles,
        Set<Long> visibleDepts,
        int aclVersion,
        String requiredPermission,
        String fileName,
        float[] embedding
) {
    public VectorRecord {
        visibleRoles = visibleRoles == null ? Set.of() : Set.copyOf(visibleRoles);
        visibleDepts = visibleDepts == null ? Set.of() : Set.copyOf(visibleDepts);
    }
}
