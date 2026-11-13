package io.aurora.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.aurora.contract.AuroraErrorCode;
import io.aurora.contract.AuroraException;
import io.aurora.rbac.PermissionCatalog;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The semantic contract shown to the model and enforced by the Gateway.
 * Registration rules are in PRD §5.2 — any violation rejects the definition.
 */
public record ToolDefinition(
        String name,
        String version,
        String description,
        JsonNode inputSchema,
        Set<String> requiredPermissions,
        SideEffect sideEffect,
        boolean idempotent,
        String compensation,
        long timeoutMs,
        ToolBudget budget,
        ApprovalPolicy approvalPolicy,
        String source
) {

    public static final String CORE_SOURCE = "core";

    public ToolDefinition {
        requiredPermissions = requiredPermissions == null
                ? Set.of() : java.util.Collections.unmodifiableSet(new LinkedHashSet<>(requiredPermissions));
        if (inputSchema == null) {
            ObjectNode schema = JsonNodeFactory.instance.objectNode();
            schema.put("type", "object");
            inputSchema = schema;
        }
        if (budget == null) {
            budget = ToolBudget.DEFAULT;
        }
        if (approvalPolicy == null) {
            approvalPolicy = ApprovalPolicy.ASK;
        }
        if (version == null || version.isBlank()) {
            version = "1.0.0";
        }
        if (source == null || source.isBlank()) {
            source = CORE_SOURCE;
        }
    }

    public boolean isPlugin() {
        return source != null && source.startsWith("plugin:");
    }

    public String pluginId() {
        return isPlugin() ? source.substring("plugin:".length()) : null;
    }

    /** Validates every §5.2 rule. RuntimeException = registration rejected. */
    public void validate(PermissionCatalog catalog) {
        if (name == null || !name.matches("[a-z][a-zA-Z0-9]*\\.[a-zA-Z0-9][a-zA-Z0-9._-]*")) {
            throw invalid("Tool 名称必须形如 resource.action");
        }
        if (description == null || description.isBlank()) {
            throw invalid("Tool 必须提供 description");
        }
        for (String permission : requiredPermissions) {
            if (permission == null || permission.isBlank() || !catalog.exists(permission)) {
                AuroraErrorCode code = isPlugin()
                        ? AuroraErrorCode.PLUGIN_UNKNOWN_PERMISSION
                        : AuroraErrorCode.INVALID_TOOL_DEFINITION;
                throw new AuroraException(code, "权限码不存在于 RBAC：" + permission);
            }
        }
        if (sideEffect == SideEffect.DESTRUCTIVE && approvalPolicy == ApprovalPolicy.AUTO) {
            throw invalid("DESTRUCTIVE 工具不能使用 AUTO（INV-4）");
        }
        if (approvalPolicy == ApprovalPolicy.AUTO
                && (sideEffect != SideEffect.READ || !idempotent)) {
            throw invalid("AUTO 仅允许 READ 且 idempotent 的工具");
        }
        if (!idempotent && sideEffect != SideEffect.READ
                && (compensation == null || compensation.isBlank())) {
            throw invalid("非只读且非幂等的 Tool 必须声明 compensation（或显式 NONE）");
        }
        if (timeoutMs <= 0 || timeoutMs > 120_000) {
            throw invalid("timeoutMs 必须在 1..120000 之间");
        }
        if (budget.maxCallsPerSession() <= 0 || budget.maxTokensPerCall() <= 0) {
            throw invalid("budget 必须为正数");
        }
        if (!List.of("core").contains(source) && !source.startsWith("plugin:")) {
            throw invalid("source 必须是 core 或 plugin:<pluginId>");
        }
    }

    private AuroraException invalid(String message) {
        return new AuroraException(AuroraErrorCode.INVALID_TOOL_DEFINITION, message);
    }

    public record ToolBudget(int maxCallsPerSession, int maxTokensPerCall) {
        public static final ToolBudget DEFAULT = new ToolBudget(5, 2000);
    }
}
