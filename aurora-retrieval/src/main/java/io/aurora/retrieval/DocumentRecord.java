package io.aurora.retrieval;

import java.time.Instant;
import java.util.Set;

public record DocumentRecord(
        String id,
        String title,
        String summary,
        String content,
        String objectType,
        String objectId,
        long ownerId,
        String visibility,
        Set<String> visibleRoles,
        Set<Long> visibleDepts,
        int aclVersion,
        int page,
        int paragraph,
        String requiredPermission,
        String storageKey,
        String mimeType,
        String fileName,
        long fileSize,
        Instant createdAt
) {
}
