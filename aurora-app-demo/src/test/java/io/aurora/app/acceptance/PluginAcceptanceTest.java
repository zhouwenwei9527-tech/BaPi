package io.aurora.app.acceptance;

import io.aurora.contract.AuroraErrorCode;
import io.aurora.contract.AuroraException;
import io.aurora.contract.Json;
import io.aurora.tool.ApprovalPolicy;
import io.aurora.tool.PluginDescriptor;
import io.aurora.tool.PluginService;
import io.aurora.tool.SideEffect;
import io.aurora.tool.ToolDefinition;
import io.aurora.tool.ToolExecutor;
import io.aurora.tool.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PluginAcceptanceTest extends BaseIntegrationTest {

    @Autowired PluginService pluginService;

    private ToolDefinition tool(String name, String permission, String source) {
        return new ToolDefinition(name, "1.0.0", "test tool", args("{\"type\":\"object\"}"),
                Set.of(permission), SideEffect.READ, true, "NONE", 3000,
                ToolDefinition.ToolBudget.DEFAULT, ApprovalPolicy.AUTO, source);
    }

    @Test
    @DisplayName("AC-21 插件声明未知权限码被整体拒绝")
    void unknownPermissionRejectsPlugin() {
        ToolDefinition bad = tool("lab.magic", "lab:magic", "plugin:bad-plugin");
        PluginDescriptor descriptor = new PluginDescriptor("bad-plugin", "Bad", "1.0.0", "1.0.0",
                "bad", "test", "bug", List.of(bad), List.of());
        assertThatThrownBy(() -> pluginService.install(descriptor))
                .isInstanceOf(AuroraException.class)
                .extracting(e -> ((AuroraException) e).code())
                .isEqualTo(AuroraErrorCode.PLUGIN_UNKNOWN_PERMISSION);
    }

    @Test
    @DisplayName("AC-22 卸载后 Tool 从注册表移除，调用返回 TOOL_NOT_REGISTERED")
    void uninstallRemovesTools() {
        ToolDefinition def = tool("temp.echo", "tool:read", "plugin:temp-test");
        ToolExecutor executor = (ctx, args) -> ToolResult.of(Json.parse("{\"ok\":true}"), "echo");
        PluginDescriptor descriptor = new PluginDescriptor("temp-test", "Temp", "1.0.0", "1.0.0",
                "temporary", "test", "box", List.of(def), List.of());
        pluginService.install(descriptor, Map.of("temp.echo", executor));
        pluginService.activate("temp-test");
        try {
            assertThat(invoke("temp.echo", "{}", admin()).status())
                    .isEqualTo(io.aurora.agent.gateway.GatewayOutcome.Status.EXECUTED);
            pluginService.uninstall("temp-test");
            var outcome = invoke("temp.echo", "{}", admin());
            assertThat(outcome.status()).isEqualTo(io.aurora.agent.gateway.GatewayOutcome.Status.DENIED);
            assertThat(outcome.errorCode()).isEqualTo("TOOL_NOT_REGISTERED");
            assertThat(toolRegistry.find("temp.echo")).isEmpty();
        } finally {
            if (pluginService.find("temp-test").isPresent()) pluginService.uninstall("temp-test");
        }
    }

    @Test
    @DisplayName("AC-23 版本不兼容插件保持 DISABLED")
    void incompatiblePluginStaysDisabled() {
        PluginDescriptor descriptor = new PluginDescriptor("future-test", "Future", "2.0.0", "9.0.0",
                "future", "test", "rocket", List.of(), List.of());
        PluginService.PluginView view = pluginService.install(descriptor);
        assertThat(view.status()).isEqualTo("DISABLED");
        assertThat(view.reason()).contains("9.0.0");
        pluginService.uninstall("future-test");
    }

    @Test
    @DisplayName("AC-24 PENDING_AUTH 插件的 Tool 调用返回 TOOL_NOT_ACTIVE")
    void pendingPluginToolIsNotActive() {
        ToolDefinition def = tool("pending.echo", "tool:read", "plugin:pending-test");
        PluginDescriptor descriptor = new PluginDescriptor("pending-test", "Pending", "1.0.0", "1.0.0",
                "pending", "test", "lock", List.of(def), List.of());
        pluginService.install(descriptor, Map.of("pending.echo",
                (ctx, args) -> ToolResult.of(Json.parse("{}"), "ok")));
        try {
            var outcome = invoke("pending.echo", "{}", admin());
            assertThat(outcome.status()).isEqualTo(io.aurora.agent.gateway.GatewayOutcome.Status.DENIED);
            assertThat(outcome.errorCode()).isEqualTo("TOOL_NOT_ACTIVE");
            pluginService.activate("pending-test");
            assertThat(invoke("pending.echo", "{}", admin()).status())
                    .isEqualTo(io.aurora.agent.gateway.GatewayOutcome.Status.EXECUTED);
        } finally {
            pluginService.uninstall("pending-test");
        }
    }
}
