package io.aurora.tool;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Result of a Tool body. Wraps data plus a human-readable summary used in
 * approval cards, trajectories and the agent reply.
 */
public record ToolResult(JsonNode data, String summary, int affectedCount) {

    public static ToolResult of(JsonNode data, String summary) {
        return new ToolResult(data, summary, 1);
    }
}
