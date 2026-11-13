package io.aurora.app.tool;

import com.fasterxml.jackson.databind.JsonNode;
import io.aurora.contract.Json;
import io.aurora.rbac.RbacViews;
import io.aurora.rbac.UserQueryService;
import io.aurora.tool.ApprovalPolicy;
import io.aurora.tool.ApprovalPresentation;
import io.aurora.tool.SideEffect;
import io.aurora.tool.ToolDefinition;
import io.aurora.tool.ToolExecutionContext;
import io.aurora.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class UserUpdateTool implements AuroraTool {

    private static final ToolDefinition DEFINITION = new ToolDefinition(
            "user.update", "1.0.0", "修改数据范围内用户的姓名、职位、部门或启停状态（写操作，默认进入审批）",
            ToolSupport.schema("""
                    {"type":"object","properties":{
                      "username":{"type":"string","minLength":1,"maxLength":64},
                      "displayName":{"type":"string","minLength":1,"maxLength":32},
                      "title":{"type":"string","minLength":1,"maxLength":64},
                      "deptId":{"type":"integer","minimum":1},
                      "status":{"type":"string","enum":["ACTIVE","DISABLED"]}
                    },"required":["username"],"additionalProperties":false}
                    """),
            Set.of("user:update"), SideEffect.WRITE, true, "NONE", 8000,
            new ToolDefinition.ToolBudget(10, 2000), ApprovalPolicy.SELF_CONFIRM, "core");

    private final UserQueryService userQueryService;

    public UserUpdateTool(UserQueryService userQueryService) {
        this.userQueryService = userQueryService;
    }

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ApprovalPresentation describe(ToolExecutionContext context, JsonNode args) {
        String username = args.path("username").asText();
        String target = username;
        try {
            target = userQueryService.findByUsername(context.principal(), username).displayName();
        } catch (RuntimeException ignored) {
            // Out-of-scope targets are still described by identifier; execution re-validates.
        }
        List<String> changes = new java.util.ArrayList<>();
        List<String> reasons = new java.util.ArrayList<>();
        int operability = 99;
        if (args.hasNonNull("displayName")) {
            changes.add("显示名称为「" + args.get("displayName").asText() + "」");
        }
        if (args.hasNonNull("title")) {
            changes.add("职位为「" + args.get("title").asText() + "」");
        }
        if (args.hasNonNull("deptId")) {
            long dept = args.get("deptId").asLong();
            changes.add("部门为「" + userQueryService.departmentName(dept) + "」");
            operability = Math.min(operability, 70);
            reasons.add("部门变更会影响该用户的数据可见范围");
        }
        if (args.hasNonNull("status")) {
            String status = args.get("status").asText();
            changes.add("状态为「" + ("ACTIVE".equals(status) ? "启用" : "停用") + "」");
            if ("DISABLED".equals(status)) {
                operability = Math.min(operability, 55);
                reasons.add("停用后该用户将无法登录");
            }
        }
        if (changes.isEmpty()) {
            return ApprovalPresentation.of(context.principal().displayName() + " 要更新 " + target
                    + " 的用户资料", 85, List.of());
        }
        return ApprovalPresentation.of(context.principal().displayName() + " 要修改 " + target
                + " 的" + String.join("、", changes), operability, reasons);
    }

    @Override
    public ToolResult execute(ToolExecutionContext context, JsonNode args) {
        Long deptId = args.hasNonNull("deptId") ? args.get("deptId").asLong() : null;
        RbacViews.UserView user = userQueryService.updateUser(context.principal(),
                args.path("username").asText(),
                args.path("displayName").asText(null),
                args.path("title").asText(null),
                deptId,
                args.path("status").asText(null));
        return new ToolResult(Json.mapper().valueToTree(user),
                "已更新用户 " + user.displayName() + "（@" + user.username() + "）", 1);
    }
}
