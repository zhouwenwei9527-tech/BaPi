package io.aurora.agent.approval;

import java.time.Instant;

public record ApprovalView(
        String id,
        String toolName,
        String toolVersion,
        String sideEffect,
        String argsSummary,
        String intentSummary,
        int operability,
        String riskLevel,
        String riskReasons,
        boolean requiresConfirmation,
        String argsHash,
        String affectedObjects,
        int affectedCount,
        String reversible,
        long requesterUserId,
        String requesterName,
        String actorType,
        String agentId,
        String status,
        String createdAt,
        String expiresAt,
        String decidedBy,
        String decidedAt,
        String executedAt,
        String resultSummary,
        String errorCode,
        String note,
        String sessionId,
        String traceId
) {

    public static ApprovalView from(ApprovalEntity e) {
        return new ApprovalView(e.id, e.toolName, e.toolVersion, e.sideEffect, e.argsSummary,
                e.intentSummary == null ? "" : e.intentSummary, e.operability, e.riskLevel,
                e.riskReasons == null ? "" : e.riskReasons, e.requiresConfirmation,
                e.argsHash.substring(0, Math.min(12, e.argsHash.length())), e.affectedObjects,
                e.affectedCount, e.reversible, e.requesterUserId, e.requesterName, e.actorType,
                e.agentId, e.status.name(), e.createdAt.toString(), e.expiresAt.toString(),
                e.decidedBy, e.decidedAt == null ? null : e.decidedAt.toString(),
                e.executedAt == null ? null : e.executedAt.toString(),
                e.resultJson == null || e.resultJson.isBlank() ? null : summarizeResult(e.resultJson),
                e.errorCode, e.note, e.sessionId, e.traceId);
    }

    private static String summarizeResult(String json) {
        return json.length() > 200 ? json.substring(0, 200) + "…" : json;
    }

    public boolean isExpired() {
        return Instant.parse(expiresAt).isBefore(Instant.now());
    }
}
