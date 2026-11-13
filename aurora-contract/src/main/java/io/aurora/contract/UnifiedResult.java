package io.aurora.contract;

import java.util.Map;

/**
 * One row of the unified retrieval surface (PRD §5.6).
 * ACTION requires requiredPermission; ENTITY / KNOWLEDGE require sourceRef.
 */
public record UnifiedResult(
        Kind type,
        String title,
        String subtitle,
        double score,
        String requiredPermission,
        SourceRef sourceRef,
        String appId,
        Map<String, Object> data
) {

    public enum Kind { ACTION, ENTITY, KNOWLEDGE }

    public record SourceRef(String objectType, String objectId, Integer page, Integer paragraph) {
    }

    public static UnifiedResult action(String title, String subtitle, double score,
                                       String permission, String appId) {
        return new UnifiedResult(Kind.ACTION, title, subtitle, score, permission, null, appId, Map.of());
    }

    public static UnifiedResult entity(String title, String subtitle, double score, String permission,
                                       String objectType, String objectId, Map<String, Object> data) {
        return new UnifiedResult(Kind.ENTITY, title, subtitle, score, permission,
                new SourceRef(objectType, objectId, null, null), null, data);
    }

    public static UnifiedResult knowledge(String title, String subtitle, double score, String permission,
                                          String objectType, String objectId, Integer page, Integer paragraph) {
        return new UnifiedResult(Kind.KNOWLEDGE, title, subtitle, score, permission,
                new SourceRef(objectType, objectId, page, paragraph), null, Map.of());
    }
}
