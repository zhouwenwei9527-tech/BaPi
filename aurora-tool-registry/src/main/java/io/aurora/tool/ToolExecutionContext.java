package io.aurora.tool;

import io.aurora.contract.Actor;
import io.aurora.contract.Json;
import io.aurora.rbac.Principal;

/**
 * Everything a Tool is allowed to know about the call. The Gateway injects
 * the resolved DataScope via Principal — Tools never build scope SQL themselves.
 */
public record ToolExecutionContext(
        Principal principal,
        Actor actor,
        String traceId,
        String sessionId,
        String idempotencyKey,
        String approvalId
) {

    public String argsDigest(com.fasterxml.jackson.databind.JsonNode args) {
        return Json.sha256(Json.canonical(args));
    }
}
