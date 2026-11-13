package io.aurora.app.tool;

import io.aurora.tool.ToolDefinition;
import io.aurora.tool.ToolExecutor;

/**
 * A tool packaged with its semantic contract. Core tools are registered at
 * startup; plugin tools are registered by the plugin lifecycle.
 */
public interface AuroraTool extends ToolExecutor {
    ToolDefinition definition();
}
