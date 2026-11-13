package io.aurora.app.web;

import io.aurora.agent.audit.AuditDraft;
import io.aurora.agent.audit.AuditService;
import io.aurora.contract.Actor;
import io.aurora.contract.ApiResponse;
import io.aurora.contract.AuroraErrorCode;
import io.aurora.contract.AuroraException;
import io.aurora.contract.Json;
import io.aurora.rbac.Principal;
import io.aurora.tool.ApprovalPolicy;
import io.aurora.tool.PluginDescriptor;
import io.aurora.tool.PluginService;
import io.aurora.tool.SideEffect;
import io.aurora.tool.ToolDefinition;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/plugins")
public class PluginController {

    private final PluginService pluginService;
    private final AuditService auditService;

    public PluginController(PluginService pluginService, AuditService auditService) {
        this.pluginService = pluginService;
        this.auditService = auditService;
    }

    @GetMapping
    public ApiResponse<List<PluginService.PluginView>> list(@RequestAttribute Principal principal) {
        if (!principal.can("plugin:manage") && !principal.can("tool:read")) {
            throw new AuroraException(AuroraErrorCode.PERMISSION_DENIED, "缺少插件查看权限");
        }
        return ApiResponse.ok(pluginService.list());
    }

    @PostMapping("/{id}/activate")
    public ApiResponse<PluginService.PluginView> activate(@RequestAttribute Principal principal,
                                                          @PathVariable String id) {
        requireManage(principal);
        audit(principal, "ACTIVATE_PLUGIN", id, "OK");
        return ApiResponse.ok(pluginService.activate(id));
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<PluginService.PluginView> disable(@RequestAttribute Principal principal,
                                                         @PathVariable String id) {
        requireManage(principal);
        audit(principal, "DISABLE_PLUGIN", id, "OK");
        return ApiResponse.ok(pluginService.disable(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> uninstall(@RequestAttribute Principal principal,
                                          @PathVariable String id) {
        requireManage(principal);
        audit(principal, "UNINSTALL_PLUGIN", id, "OK");
        pluginService.uninstall(id);
        return ApiResponse.ok(true);
    }

    @PostMapping("/demo/unknown-permission")
    public ApiResponse<PluginService.PluginView> installUnknownPermission(
            @RequestAttribute Principal principal) {
        requireManage(principal);
        ToolDefinition bad = new ToolDefinition("lab.magic", "1.0.0", "声明了未知权限的示例 Tool",
                Json.parse("{\"type\":\"object\"}"), Set.of("lab:magic"), SideEffect.READ, true,
                "NONE", 5000, ToolDefinition.ToolBudget.DEFAULT, ApprovalPolicy.AUTO,
                "plugin:broken-plugin");
        PluginDescriptor descriptor = new PluginDescriptor("broken-plugin", "越权示例插件", "0.1.0",
                "1.0.0", "用于验证插件不能声明 RBAC 中不存在的权限码。", "Red Team", "bug",
                List.of(bad), List.of());
        try {
            return ApiResponse.ok(pluginService.install(descriptor));
        } catch (AuroraException e) {
            audit(principal, "PLUGIN_INSTALL_REJECTED", "broken-plugin", "ERROR");
            throw e;
        }
    }

    @PostMapping("/demo/incompatible")
    public ApiResponse<PluginService.PluginView> installIncompatible(
            @RequestAttribute Principal principal) {
        requireManage(principal);
        PluginDescriptor descriptor = new PluginDescriptor("future-plugin", "未来版本插件", "2.0.0",
                "9.0.0", "需要更高版本框架，安装后保持 DISABLED。", "Aurora Labs", "rocket",
                List.of(), List.of());
        audit(principal, "INSTALL_INCOMPATIBLE_PLUGIN", "future-plugin", "OK");
        return ApiResponse.ok(pluginService.install(descriptor));
    }

    private void requireManage(Principal principal) {
        if (!principal.can("plugin:manage")) {
            throw new AuroraException(AuroraErrorCode.PERMISSION_DENIED, "缺少 plugin:manage 权限");
        }
    }

    private void audit(Principal principal, String decision, String pluginId, String status) {
        auditService.record(AuditDraft.of(AuditService.TYPE_PLUGIN)
                .trace("plugin-" + java.util.UUID.randomUUID()).session("plugins")
                .actor(Actor.user()).onBehalfOf(principal)
                .decision(decision).status(status)
                .refs(List.of(Map.of("objectType", "plugin", "objectId", pluginId))));
    }
}
