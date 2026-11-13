package io.aurora.app.tool;

import com.fasterxml.jackson.databind.JsonNode;
import io.aurora.contract.UnifiedResult;
import io.aurora.tool.ApprovalPolicy;
import io.aurora.tool.SideEffect;
import io.aurora.tool.ToolDefinition;
import io.aurora.tool.ToolExecutionContext;
import io.aurora.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Plugin tool: literature search. Declared by the paper-search plugin and
 * rendered by the shell's palette card, never by plugin JS.
 */
@Component
public class PaperSearchTool implements AuroraTool {

    private static final ToolDefinition DEFINITION = new ToolDefinition(
            "paper.search", "1.2.0", "检索科研文献摘要（插件 paper-search 提供）",
            ToolSupport.schema("""
                    {"type":"object","properties":{
                      "query":{"type":"string","minLength":1,"maxLength":200},
                      "limit":{"type":"integer","minimum":1,"maximum":10}
                    },"required":["query"],"additionalProperties":false}
                    """),
            Set.of("knowledge:public"), SideEffect.READ, true, "NONE", 6000,
            new ToolDefinition.ToolBudget(10, 2000), ApprovalPolicy.AUTO, "plugin:paper-search");

    private static final List<Map<String, Object>> PAPERS = List.of(
            Map.of("title", "Retrieval-Augmented Generation for Knowledge-Intensive NLP",
                    "authors", "Lewis et al.", "year", 2020, "venue", "NeurIPS",
                    "abstract", "结合参数化与非参数化记忆的检索增强生成方法，显著提升知识密集型任务表现。"),
            Map.of("title", "ColPali: Efficient Document Retrieval with Vision Language Models",
                    "authors", "Faysse et al.", "year", 2024, "venue", "ICLR",
                    "abstract", "直接用视觉语言模型编码文档页面图像，避免 OCR 误差，提升多模态检索效果。"),
            Map.of("title", "Lost in the Middle: How Language Models Use Long Contexts",
                    "authors", "Liu et al.", "year", 2023, "venue", "TACL",
                    "abstract", "长上下文模型中位于中间位置的信息更容易被忽略，对检索片段排序有直接启示。"),
            Map.of("title", "Access Control for Retrieval-Augmented Generation",
                    "authors", "Zeng et al.", "year", 2024, "venue", "arXiv",
                    "abstract", "提出在检索召回阶段前置访问范围过滤的架构，避免先召回后过滤导致的泄露。"),
            Map.of("title", "Hybrid Search with Dense and Sparse Representations",
                    "authors", "Karpukhin et al.", "year", 2020, "venue", "EMNLP",
                    "abstract", "稠密向量与稀疏 BM25 的混合检索在开放域问答上取得稳定收益。"));

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ToolResult execute(ToolExecutionContext context, JsonNode args) {
        String query = args.path("query").asText("");
        int limit = Math.max(1, Math.min(args.path("limit").asInt(5), PAPERS.size()));
        List<Map<String, Object>> matched = PAPERS.stream()
                .filter(p -> query.isBlank() || (p.get("title") + " " + p.get("abstract"))
                        .toLowerCase().contains(query.toLowerCase()))
                .limit(limit)
                .toList();
        var results = matched.stream().map(p -> UnifiedResult.knowledge(
                String.valueOf(p.get("title")),
                p.get("authors") + " · " + p.get("year") + " · " + p.get("venue"),
                0.81, "knowledge:public", "paper", String.valueOf(p.get("title")),
                1, 1)).toList();
        var data = ToolSupport.object();
        data.set("results", ToolSupport.arrayOf(results));
        data.put("provider", "paper-search-plugin");
        return new ToolResult(data, "文献插件返回 " + results.size() + " 条结果", results.size());
    }
}
