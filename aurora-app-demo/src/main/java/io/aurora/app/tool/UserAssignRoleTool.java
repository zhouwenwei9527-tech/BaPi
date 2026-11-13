package io.aurora.app.tool;

import com.fasterxml.jackson.databind.JsonNode;
import io.aurora.contract.Json;
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
public class UserAssignRoleTool implements AuroraTool {

    private static final ToolDefinition DEFINITION = new ToolDefinition(
            "user.assignRole", "1.0.0", "给数据范围内的用户授予一个角色（提权操作，需要另一位成员复核）",
            ToolSupport.schema("""
                    {"type":"object","properties":{
                      "username":{"type":"string","minLength":1,"maxLength":64},
                      "roleCode":{"type":"string","enum":["ops","finance","guest","admin"]}
                    },"required":["username","roleCode"],"additionalProperties":false}
                    """),
            Set.of("user:update"), SideEffect.WRITE, false, "user.revokeRole", 8000,
            new ToolDefinition.ToolBudget(5, 2000), ApprovalPolicy.ASK, "core");

    private final UserQueryService userQueryService;

    public UserAssignRoleTool(UserQueryService userQueryService) {
        this.userQueryService = userQueryService;
    }

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ApprovalPresentation describe(ToolExecutionContext context, JsonNode args) {
        String username = args.path("username").asText();
        String roleCode = args.path("roleCode").asText();
        String target = username;
        try {
            target = userQueryService.findByUsername(context.principal(), username).displayName();
        } catch (RuntimeException ignored) {
            // Scope is re-validated at execution.
        }
        String roleName = userQueryService.roleName(roleCode);
        int operability = switch (roleCode) {
            case "admin" -> 5;
            case "security" -> 10;
            case "finance" -> 60;
            case "ops" -> 75;
            default -> 90;
        };
        java.util.List<String> reasons = new java.util.ArrayList<>();
        if ("admin".equals(roleCode)) {
            reasons.add("管理员拥有通配 * 全量权限");
            reasons.add("该变更可能造成权限大幅提升");
        } else if ("security".equals(roleCode)) {
            reasons.add("安全审计员拥有全量业务权限与审批权");
        } else if ("finance".equals(roleCode)) {
            reasons.add("财务角色会获得财务数据与薪酬文档访问权");
        }
        return ApprovalPresentation.of(context.principal().displayName() + " 要将 " + target
                + " 的角色调整为「" + roleName + "（" + roleCode + "）」", operability, reasons);
    }

    @Override
    public ToolResult execute(ToolExecutionContext context, JsonNode args) {
        UserQueryService.AssignResult result = userQueryService.assignRole(context.principal(),
                args.path("username").asText(), args.path("roleCode").asText());
        var data = Json.mapper().valueToTree(result);
        return new ToolResult(data,
                "为 " + result.displayName() + " 授予 " + result.roleName()
                        + (result.changed() ? "" : "（原本已拥有）"), result.changed() ? 1 : 0);
    }
}
