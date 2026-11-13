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
public class UserDeleteTool implements AuroraTool {

    private static final ToolDefinition DEFINITION = new ToolDefinition(
            "user.delete", "1.0.0", "删除数据范围内的用户（破坏性、不可回滚，必须人工审批且不得自批）",
            ToolSupport.schema("""
                    {"type":"object","properties":{
                      "username":{"type":"string","minLength":1,"maxLength":64}
                    },"required":["username"],"additionalProperties":false}
                    """),
            Set.of("user:delete"), SideEffect.DESTRUCTIVE, false, "NONE", 8000,
            new ToolDefinition.ToolBudget(3, 1200), ApprovalPolicy.SELF_CONFIRM, "core");

    private final UserQueryService userQueryService;

    public UserDeleteTool(UserQueryService userQueryService) {
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
            // Scope is re-validated at execution.
        }
        return ApprovalPresentation.of(context.principal().displayName() + " 要永久删除用户「"
                + target + "（@" + username + "）」", 15,
                java.util.List.of("删除用户及其角色关联", "该操作不可回滚，需要谨慎确认"));
    }

    @Override
    public ToolResult execute(ToolExecutionContext context, JsonNode args) {
        UserQueryService.DeleteResult result = userQueryService.deleteUser(context.principal(),
                args.path("username").asText());
        return new ToolResult(Json.mapper().valueToTree(result),
                "删除用户 " + result.displayName() + "，移除 " + result.rolesRemoved() + " 个角色关联", 1);
    }
}
