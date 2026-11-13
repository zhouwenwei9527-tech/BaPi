package io.aurora.tool;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Implemented by every callable system capability. Only ToolInvocationGateway
 * is allowed to call this interface (enforced by ArchUnit, AC-25).
 */
public interface ToolExecutor {
    ToolResult execute(ToolExecutionContext context, JsonNode args);

    /**
     * Optional human-readable approval context. It never executes anything;
     * the Gateway uses it when creating an approval task.
     */
    default ApprovalPresentation describe(ToolExecutionContext context, JsonNode args) {
        return ApprovalPresentation.generic();
    }
}
