package io.aurora.agent.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import io.aurora.tool.ApprovalPresentation;

import java.util.List;

/**
 * Outcome of one Gateway invocation. Every exit carries a stable code and a
 * human-readable trace of the pipeline it went through.
 */
public record GatewayOutcome(
        Status status,
        String errorCode,
        String message,
        JsonNode data,
        String summary,
        int affectedCount,
        String approvalId,
        String traceId,
        String sessionId,
        Long auditSeq,
        long latencyMs,
        List<PipelineStep> steps,
        ApprovalPresentation confirmation
) {

    public enum Status { EXECUTED, PENDING, CONFIRMATION_REQUIRED, DENIED, FAILED }

    public record PipelineStep(String stage, String status, String detail) {
    }

    public static GatewayOutcome executed(JsonNode data, String summary, int affectedCount,
                                          String traceId, String sessionId, Long auditSeq,
                                          long latencyMs, List<PipelineStep> steps) {
        return new GatewayOutcome(Status.EXECUTED, null, summary, data, summary, affectedCount,
                null, traceId, sessionId, auditSeq, latencyMs, steps, null);
    }

    public static GatewayOutcome pending(String approvalId, String traceId, String sessionId,
                                         Long auditSeq, List<PipelineStep> steps) {
        return new GatewayOutcome(Status.PENDING, null, "已创建审批任务，批准后才会执行",
                null, null, 0, approvalId, traceId, sessionId, auditSeq, 0, steps, null);
    }

    public static GatewayOutcome confirmationRequired(ApprovalPresentation confirmation,
                                                      String traceId, String sessionId,
                                                      Long auditSeq, List<PipelineStep> steps) {
        return new GatewayOutcome(Status.CONFIRMATION_REQUIRED, null,
                "这项操作可操作度较低，请确认风险后再执行。",
                null, null, 0, null, traceId, sessionId, auditSeq, 0, steps, confirmation);
    }

    public static GatewayOutcome denied(String code, String message, String traceId, String sessionId,
                                        Long auditSeq, List<PipelineStep> steps) {
        return new GatewayOutcome(Status.DENIED, code, message, null, null, 0, null,
                traceId, sessionId, auditSeq, 0, steps, null);
    }

    public static GatewayOutcome failed(String code, String message, String traceId, String sessionId,
                                        Long auditSeq, List<PipelineStep> steps) {
        return new GatewayOutcome(Status.FAILED, code, message, null, null, 0, null,
                traceId, sessionId, auditSeq, 0, steps, null);
    }
}
