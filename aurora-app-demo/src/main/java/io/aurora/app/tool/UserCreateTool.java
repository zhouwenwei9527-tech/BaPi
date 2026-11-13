package io.aurora.app.tool;

import com.fasterxml.jackson.databind.JsonNode;
import io.aurora.contract.Json;
import io.aurora.rbac.RbacViews;
import io.aurora.rbac.UserQueryService;
import io.aurora.tool.ApprovalPresentation;
import io.aurora.tool.ApprovalPolicy;
import io.aurora.tool.SideEffect;
import io.aurora.tool.ToolDefinition;
import io.aurora.tool.ToolExecutionContext;
import io.aurora.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class UserCreateTool implements AuroraTool {

    private static final ToolDefinition DEFINITION = new ToolDefinition(
            "user.create", "1.0.0", "在数据范围内创建用户并可选授予角色（写操作，默认进入审批）",
            ToolSupport.schema("""
                    {"type":"object","properties":{
                      "username":{"type":"string","pattern":"^[a-z0-9_.-]{3,32}$"},
                      "displayName":{"type":"string","minLength":1,"maxLength":32},
                      "title":{"type":"string","maxLength":64},
                      "deptId":{"type":"integer","minimum":1},
                      "password":{"type":"string","minLength":6,"maxLength":64},
                      "roleCode":{"type":"string","maxLength":32}
                    },"required":["username","displayName","deptId","password"],
                    "additionalProperties":false}
                    """),
            Set.of("user:update"), SideEffect.WRITE, false, "user.delete", 8000,
            new ToolDefinition.ToolBudget(10, 2500), ApprovalPolicy.SELF_CONFIRM, "core");

    private final UserQueryService userQueryService;

    public UserCreateTool(UserQueryService userQueryService) {
        this.userQueryService = userQueryService;
    }

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ApprovalPresentation describe(ToolExecutionContext context, JsonNode args) {
        String username = args.path("username").asText();
        String displayName = args.path("displayName").asText(username);
        String roleCode = args.path("roleCode").asText("");
        String roleName = roleCode.isBlank() ? "无角色" : userQueryService.roleName(roleCode);
        int operability = switch (roleCode) {
            case "admin" -> 5;
            case "security" -> 10;
            default -> 88;
        };
        java.util.List<String> reasons = new java.util.ArrayList<>();
        if ("admin".equals(roleCode) || "security".equals(roleCode)) {
            reasons.add("新账号将直接获得全量权限");
        }
        return ApprovalPresentation.of(context.principal().displayName() + " 要创建用户「" + displayName
                + "（@" + username + "）」并授予「" + roleName + "」角色", operability, reasons);
    }

    @Override
    public ToolResult execute(ToolExecutionContext context, JsonNode args) {
        RbacViews.UserView user = userQueryService.createUser(context.principal(),
                args.path("username").asText(),
                args.path("displayName").asText(),
                args.path("title").asText(""),
                args.path("deptId").asLong(),
                args.path("password").asText(),
                args.path("roleCode").asText(null));
        return new ToolResult(Json.mapper().valueToTree(user),
                "已创建用户 " + user.displayName() + "（@" + user.username() + "）", 1);
    }
}
