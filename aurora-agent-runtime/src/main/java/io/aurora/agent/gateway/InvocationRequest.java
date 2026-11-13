package io.aurora.agent.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import io.aurora.contract.Actor;
import io.aurora.rbac.Principal;

/**
 * Input to the only path that can execute a Tool.
 * {@code confirmed} means the caller has explicitly acknowledged a risk
 * warning shown by the UI; it never replaces permission checks.
 */
public record InvocationRequest(
        String toolName,
        JsonNode args,
        Principal principal,
        Actor actor,
        String sessionId,
        String traceId,
        String idempotencyKey,
        boolean confirmed
) {

    public InvocationRequest {
        if (args == null) {
            args = io.aurora.contract.Json.mapper().createObjectNode();
        }
    }

    public InvocationRequest(String toolName, JsonNode args, Principal principal, Actor actor,
                             String sessionId, String traceId, String idempotencyKey) {
        this(toolName, args, principal, actor, sessionId, traceId, idempotencyKey, false);
    }
}
