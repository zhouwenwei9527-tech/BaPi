package io.aurora.app.tool;

import com.fasterxml.jackson.databind.JsonNode;
import io.aurora.contract.AuroraErrorCode;
import io.aurora.contract.AuroraException;
import io.aurora.contract.Json;
import io.aurora.retrieval.DocumentRecord;
import io.aurora.retrieval.DocumentService;
import io.aurora.tool.ApprovalPresentation;
import io.aurora.tool.ApprovalPolicy;
import io.aurora.tool.SideEffect;
import io.aurora.tool.ToolDefinition;
import io.aurora.tool.ToolExecutionContext;
import io.aurora.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

@Component
public class FileShareTool implements AuroraTool {

    private static final ToolDefinition DEFINITION = new ToolDefinition(
            "file.share", "1.0.0", "把一个可见文件以短时效安全链接共享给外部对象（写操作，进入审批）",
            ToolSupport.schema("""
                    {"type":"object","properties":{
                      "docId":{"type":"string","minLength":1,"maxLength":36},
                      "to":{"type":"string","minLength":3,"maxLength":120},
                      "expiresHours":{"type":"integer","minimum":1,"maximum":168}
                    },"required":["docId","to"],"additionalProperties":false}
                    """),
            Set.of("file:read"), SideEffect.WRITE, false, "NONE", 8000,
            new ToolDefinition.ToolBudget(5, 1200), ApprovalPolicy.SELF_CONFIRM, "core");

    private final DocumentService documentService;

    public FileShareTool(DocumentService documentService) {
        this.documentService = documentService;
    }

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ApprovalPresentation describe(ToolExecutionContext context, JsonNode args) {
        String docId = args.path("docId").asText();
        String to = args.path("to").asText();
        return ApprovalPresentation.of(context.principal().displayName() + " 要将文件 " + docId
                + " 共享给「" + to + "」，链接有效期 5 分钟", 70,
                java.util.List.of("会生成对外可访问的短时效链接"));
    }

    @Override
    public ToolResult execute(ToolExecutionContext context, JsonNode args) {
        DocumentRecord doc = documentService.requireVisible(context.principal(),
                args.path("docId").asText());
        int hours = Math.max(1, Math.min(args.path("expiresHours").asInt(24), 168));
        String url = documentService.storage().presign(doc.storageKey(), Duration.ofMinutes(5));
        if (url == null) {
            throw new AuroraException(AuroraErrorCode.TOOL_FAILED,
                    "文件服务不可用，无法生成短时效链接");
        }
        var data = ToolSupport.object();
        data.put("docId", doc.id());
        data.put("fileName", doc.fileName());
        data.put("sharedWith", args.path("to").asText());
        data.put("url", url);
        data.put("urlTtlMinutes", 5);
        data.put("shareExpiresAt", Instant.now().plus(Duration.ofHours(hours)).toString());
        return new ToolResult(data, "已为 " + doc.fileName() + " 生成短时效共享链接", 1);
    }
}
