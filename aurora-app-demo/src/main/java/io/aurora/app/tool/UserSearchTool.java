package io.aurora.app.tool;

import com.fasterxml.jackson.databind.JsonNode;
import io.aurora.rbac.RbacViews;
import io.aurora.rbac.UserQueryService;
import io.aurora.tool.ApprovalPolicy;
import io.aurora.tool.SideEffect;
import io.aurora.tool.ToolDefinition;
import io.aurora.tool.ToolExecutionContext;
import io.aurora.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class UserSearchTool implements AuroraTool {

    private static final ToolDefinition DEFINITION = new ToolDefinition(
            "user.search", "1.0.0", "按关键词查询当前用户数据范围内的用户，返回姓名、部门与角色",
            ToolSupport.schema("""
                    {"type":"object","properties":{
                      "query":{"type":"string","maxLength":80},
                      "limit":{"type":"integer","minimum":1,"maximum":50}
                    },"required":[],"additionalProperties":false}
                    """),
            Set.of("user:read"), SideEffect.READ, true, "NONE", 5000,
            new ToolDefinition.ToolBudget(20, 2000), ApprovalPolicy.AUTO, "core");

    private final UserQueryService userQueryService;

    public UserSearchTool(UserQueryService userQueryService) {
        this.userQueryService = userQueryService;
    }

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ToolResult execute(ToolExecutionContext context, JsonNode args) {
        String query = args.path("query").asText("");
        int limit = args.path("limit").asInt(20);
        List<RbacViews.UserView> users = userQueryService.search(context.principal(), query, limit);
        var data = ToolSupport.object();
        data.set("users", ToolSupport.arrayOf(users));
        data.put("scope", context.principal().dataScope().type().name());
        data.put("trimmedByScope", true);
        String summary = "找到 " + users.size() + " 位可见用户（数据范围 "
                + context.principal().dataScope().type() + "）";
        return new ToolResult(data, summary, users.size());
    }
}
