package io.aurora.app.tool;

import com.fasterxml.jackson.databind.JsonNode;
import io.aurora.contract.UnifiedResult;
import io.aurora.retrieval.RetrievalService;
import io.aurora.tool.ApprovalPolicy;
import io.aurora.tool.SideEffect;
import io.aurora.tool.ToolDefinition;
import io.aurora.tool.ToolExecutionContext;
import io.aurora.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class KnowledgeSearchTool implements AuroraTool {

    private static final ToolDefinition DEFINITION = new ToolDefinition(
            "knowledge.search", "1.0.0", "统一检索：返回当前用户有权访问的知识切片、实体与操作",
            ToolSupport.schema("""
                    {"type":"object","properties":{
                      "query":{"type":"string","maxLength":400},
                      "limit":{"type":"integer","minimum":1,"maximum":20}
                    },"required":["query"],"additionalProperties":false}
                    """),
            Set.of("knowledge:public"), SideEffect.READ, true, "NONE", 8000,
            new ToolDefinition.ToolBudget(20, 2500), ApprovalPolicy.AUTO, "core");

    private final RetrievalService retrievalService;

    public KnowledgeSearchTool(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ToolResult execute(ToolExecutionContext context, JsonNode args) {
        List<UnifiedResult> results = retrievalService.knowledgeOnly(context.principal(),
                args.path("query").asText(""), args.path("limit").asInt(6));
        var data = ToolSupport.object();
        data.set("results", ToolSupport.arrayOf(results));
        return new ToolResult(data, "检索到 " + results.size() + " 条可访问的知识切片", results.size());
    }
}
