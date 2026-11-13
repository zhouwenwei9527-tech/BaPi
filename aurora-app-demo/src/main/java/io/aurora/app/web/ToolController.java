package io.aurora.app.web;

import com.fasterxml.jackson.databind.JsonNode;
import io.aurora.agent.gateway.GatewayOutcome;
import io.aurora.agent.gateway.InvocationRequest;
import io.aurora.agent.gateway.ToolInvocationGateway;
import io.aurora.contract.Actor;
import io.aurora.contract.ApiResponse;
import io.aurora.contract.AuroraErrorCode;
import io.aurora.contract.AuroraException;
import io.aurora.contract.Json;
import io.aurora.rbac.Principal;
import io.aurora.tool.ToolRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tools")
public class ToolController {

    private final ToolRegistry toolRegistry;
    private final ToolInvocationGateway gateway;

    public ToolController(ToolRegistry toolRegistry, ToolInvocationGateway gateway) {
        this.toolRegistry = toolRegistry;
        this.gateway = gateway;
    }

    @GetMapping
    public ApiResponse<List<ToolRegistry.ToolView>> list(@RequestAttribute Principal principal) {
        if (!principal.can("tool:read")) {
            throw new AuroraException(AuroraErrorCode.PERMISSION_DENIED, "缺少 tool:read 权限");
        }
        return ApiResponse.ok(toolRegistry.list());
    }

    @PostMapping("/{name}/invoke")
    public ApiResponse<GatewayOutcome> invoke(@RequestAttribute Principal principal,
                                              @PathVariable String name,
                                              @RequestBody(required = false) InvokeRequest body) {
        InvokeRequest request = body == null
                ? new InvokeRequest(Json.mapper().createObjectNode(), null, null, null) : body;
        JsonNode args = request.args() == null ? Json.mapper().createObjectNode() : request.args();
        String sessionId = request.sessionId() == null || request.sessionId().isBlank()
                ? "manual-" + principal.userId() : request.sessionId();
        GatewayOutcome outcome = gateway.invoke(new InvocationRequest(name, args, principal,
                Actor.user(), sessionId, "tr-" + UUID.randomUUID(),
                request.idempotencyKey(), Boolean.TRUE.equals(request.confirm())));
        return ApiResponse.ok(outcome);
    }

    public record InvokeRequest(JsonNode args, String sessionId, String idempotencyKey,
                                Boolean confirm) {
    }
}
