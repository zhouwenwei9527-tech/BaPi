package io.aurora.tool;

import io.aurora.contract.AuroraErrorCode;
import io.aurora.contract.AuroraException;
import io.aurora.rbac.PermissionCatalog;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The single source of truth for callable tools. Unknown = rejected (INV-2).
 */
@Service
public class ToolRegistry {

    private final Map<String, RegisteredTool> tools = new ConcurrentHashMap<>();
    private final PermissionCatalog permissionCatalog;

    public ToolRegistry(PermissionCatalog permissionCatalog) {
        this.permissionCatalog = permissionCatalog;
    }

    public synchronized void register(ToolDefinition definition, ToolExecutor executor, boolean active) {
        definition.validate(permissionCatalog);
        if (tools.containsKey(definition.name())) {
            throw new AuroraException(AuroraErrorCode.INVALID_TOOL_DEFINITION,
                    "Tool 已注册：" + definition.name());
        }
        tools.put(definition.name(), new RegisteredTool(definition, executor, active));
    }

    public synchronized void registerCore(ToolDefinition definition, ToolExecutor executor) {
        register(definition, executor, true);
    }

    public Optional<RegisteredTool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public boolean isActive(String name) {
        RegisteredTool tool = tools.get(name);
        return tool != null && tool.active;
    }

    public void setSourceActive(String source, boolean active) {
        tools.replaceAll((name, tool) -> tool.definition().source().equals(source)
                ? new RegisteredTool(tool.definition(), tool.executor(), active) : tool);
    }

    public synchronized int unregisterSource(String source) {
        int before = tools.size();
        tools.entrySet().removeIf(e -> e.getValue().definition().source().equals(source));
        return before - tools.size();
    }

    public List<ToolView> list() {
        return tools.values().stream()
                .map(t -> new ToolView(t.definition().name(), t.definition().version(),
                        t.definition().description(), t.definition().sideEffect(),
                        t.definition().approvalPolicy(), t.definition().requiredPermissions(),
                        t.definition().source(), t.active, t.definition().idempotent(),
                        t.definition().compensation(), t.definition().inputSchema()))
                .sorted(Comparator.comparing(ToolView::name))
                .toList();
    }

    public int size() {
        return tools.size();
    }

    public record RegisteredTool(ToolDefinition definition, ToolExecutor executor, boolean active) {
    }

    public record ToolView(String name, String version, String description, SideEffect sideEffect,
                           ApprovalPolicy approvalPolicy, java.util.Set<String> requiredPermissions,
                           String source, boolean active, boolean idempotent, String compensation,
                           com.fasterxml.jackson.databind.JsonNode inputSchema) {
    }
}
