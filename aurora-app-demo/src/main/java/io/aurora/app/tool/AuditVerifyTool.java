package io.aurora.app.tool;

import com.fasterxml.jackson.databind.JsonNode;
import io.aurora.agent.audit.AuditService;
import io.aurora.tool.ApprovalPolicy;
import io.aurora.tool.SideEffect;
import io.aurora.tool.ToolDefinition;
import io.aurora.tool.ToolExecutionContext;
import io.aurora.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class AuditVerifyTool implements AuroraTool {

    private static final ToolDefinition DEFINITION = new ToolDefinition(
            "audit.verify", "1.0.0", "校验审计事件哈希链是否完整，返回断裂位置",
            ToolSupport.schema("{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}"),
            Set.of("audit:read"), SideEffect.READ, true, "NONE", 8000,
            new ToolDefinition.ToolBudget(10, 800), ApprovalPolicy.AUTO, "core");

    private final AuditService auditService;

    public AuditVerifyTool(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ToolResult execute(ToolExecutionContext context, JsonNode args) {
        var verification = auditService.verifyChain();
        var data = ToolSupport.object();
        data.put("valid", verification.valid());
        data.put("checked", verification.checked());
        if (verification.brokenAt() != null) {
            data.put("brokenAt", verification.brokenAt());
        }
        data.put("message", verification.message());
        return new ToolResult(data, verification.message(), 1);
    }
}
