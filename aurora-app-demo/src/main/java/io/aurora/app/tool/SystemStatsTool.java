package io.aurora.app.tool;

import com.fasterxml.jackson.databind.JsonNode;
import io.aurora.agent.approval.ApprovalService;
import io.aurora.agent.audit.AuditService;
import io.aurora.retrieval.DocumentRepository;
import io.aurora.tool.ApprovalPolicy;
import io.aurora.tool.SideEffect;
import io.aurora.tool.ToolDefinition;
import io.aurora.tool.ToolExecutionContext;
import io.aurora.tool.ToolRegistry;
import io.aurora.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class SystemStatsTool implements AuroraTool {

    private static final ToolDefinition DEFINITION = new ToolDefinition(
            "system.stats", "1.0.0", "返回框架自身的关键计数：Tool、审批、审计、知识文档",
            ToolSupport.schema("{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}"),
            Set.of("tool:read"), SideEffect.READ, true, "NONE", 5000,
            new ToolDefinition.ToolBudget(10, 500), ApprovalPolicy.AUTO, "core");

    private final ToolRegistry toolRegistry;
    private final ApprovalService approvalService;
    private final AuditService auditService;
    private final DocumentRepository documentRepository;

    public SystemStatsTool(ToolRegistry toolRegistry, ApprovalService approvalService,
                           AuditService auditService, DocumentRepository documentRepository) {
        this.toolRegistry = toolRegistry;
        this.approvalService = approvalService;
        this.auditService = auditService;
        this.documentRepository = documentRepository;
    }

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ToolResult execute(ToolExecutionContext context, JsonNode args) {
        var data = ToolSupport.object();
        data.put("tools", toolRegistry.size());
        data.put("pendingApprovals", approvalService.pendingCount());
        data.put("auditEvents", auditService.count());
        data.put("documents", documentRepository.count());
        return new ToolResult(data, "框架统计已刷新", 1);
    }
}
