package io.aurora.agent.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Provider-agnostic model boundary. The Gateway — never the model — executes
 * tool calls (INV-1); this interface only proposes them.
 */
public interface ModelPort {

    String provider();

    ModelTurn complete(ModelRequest request);

    record ModelRequest(String userMessage, List<ToolSummary> tools, List<String> history,
                        String lastToolName, String lastToolSummary, JsonNode lastToolResult) {
    }

    record ToolSummary(String name, String description, JsonNode inputSchema, String sideEffect) {
    }

    record ModelTurn(String text, ToolCall toolCall, int tokensUsed) {
        public static ModelTurn text(String text, int tokensUsed) {
            return new ModelTurn(text, null, tokensUsed);
        }

        public static ModelTurn call(String name, JsonNode args, int tokensUsed) {
            return new ModelTurn(null, new ToolCall(name, args), tokensUsed);
        }
    }

    record ToolCall(String name, JsonNode args) {
    }
}
