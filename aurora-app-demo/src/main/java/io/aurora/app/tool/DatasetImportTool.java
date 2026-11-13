package io.aurora.app.tool;

import com.fasterxml.jackson.databind.JsonNode;
import io.aurora.contract.AuroraErrorCode;
import io.aurora.contract.AuroraException;
import io.aurora.tool.ApprovalPresentation;
import io.aurora.tool.ApprovalPolicy;
import io.aurora.tool.SideEffect;
import io.aurora.tool.ToolDefinition;
import io.aurora.tool.ToolExecutionContext;
import io.aurora.tool.ToolResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Plugin tool: import an experiment dataset. It is WRITE + ASK and its plugin
 * starts in PENDING_AUTH, so calls return TOOL_NOT_ACTIVE until an admin
 * activates the plugin (AC-24).
 */
@Component
public class DatasetImportTool implements AuroraTool {

    private static final ToolDefinition DEFINITION = new ToolDefinition(
            "dataset.import", "0.9.1", "导入实验数据集（插件 lab-data 提供，写操作需审批）",
            ToolSupport.schema("""
                    {"type":"object","properties":{
                      "datasetName":{"type":"string","minLength":1,"maxLength":120},
                      "rows":{"type":"integer","minimum":1,"maximum":100000}
                    },"required":["datasetName"],"additionalProperties":false}
                    """),
            Set.of("file:read"), SideEffect.WRITE, false, "NONE", 12000,
            new ToolDefinition.ToolBudget(3, 1500), ApprovalPolicy.SELF_CONFIRM, "plugin:lab-data");

    private final JdbcTemplate jdbc;

    public DatasetImportTool(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ApprovalPresentation describe(ToolExecutionContext context, JsonNode args) {
        String dataset = args.path("datasetName").asText();
        int rows = args.path("rows").asInt(100);
        return ApprovalPresentation.of(context.principal().displayName() + " 要导入数据集「"
                + dataset + "」，预计写入 " + rows + " 行", 75,
                java.util.List.of("会向实验数据表写入数据"));
    }

    @Override
    public ToolResult execute(ToolExecutionContext context, JsonNode args) {
        String name = args.path("datasetName").asText();
        int rows = Math.max(1, Math.min(args.path("rows").asInt(100), 100000));
        if (name.isBlank()) {
            throw new AuroraException(AuroraErrorCode.SCHEMA_INVALID, "datasetName 不能为空");
        }
        jdbc.update("""
                INSERT INTO dataset_import_log (dataset_name, row_count, imported_by, trace_id)
                VALUES (?, ?, ?, ?)
                """, name, rows, context.principal().userId(), context.traceId());
        var data = ToolSupport.object();
        data.put("datasetName", name);
        data.put("rows", rows);
        data.put("importedBy", context.principal().displayName());
        return new ToolResult(data, "已导入数据集 " + name + "（" + rows + " 行）", rows);
    }
}
