package io.aurora.tool;

import com.fasterxml.jackson.databind.JsonNode;
import io.aurora.contract.AuroraErrorCode;
import io.aurora.contract.AuroraException;
import io.aurora.rbac.PermissionCatalog;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plugin lifecycle: INSTALLED → PENDING_AUTH → ACTIVE ↔ DISABLED, plus uninstall.
 * A plugin never gets more authority than RBAC already understands (INV-8).
 */
@Service
public class PluginService {

    public static final String FRAMEWORK_VERSION = "1.0.0";

    public enum Status { INSTALLED, PENDING_AUTH, ACTIVE, DISABLED }

    private final Map<String, PluginState> plugins = new ConcurrentHashMap<>();
    private final ToolRegistry toolRegistry;
    private final PermissionCatalog permissionCatalog;

    public PluginService(ToolRegistry toolRegistry, PermissionCatalog permissionCatalog) {
        this.toolRegistry = toolRegistry;
        this.permissionCatalog = permissionCatalog;
    }

    /**
     * Installs a plugin descriptor. Unknown permission codes reject the whole
     * install. Incompatible framework versions install as DISABLED with a reason.
     */
    public PluginView install(PluginDescriptor descriptor) {
        return install(descriptor, Map.of());
    }

    public PluginView install(PluginDescriptor descriptor, Map<String, ToolExecutor> executors) {
        if (plugins.containsKey(descriptor.pluginId())) {
            throw new AuroraException(AuroraErrorCode.CONFLICT, "插件已安装：" + descriptor.pluginId());
        }
        for (ToolDefinition tool : descriptor.tools()) {
            if (!("plugin:" + descriptor.pluginId()).equals(tool.source())) {
                throw new AuroraException(AuroraErrorCode.INVALID_TOOL_DEFINITION,
                        "插件 Tool 的 source 必须是 plugin:" + descriptor.pluginId());
            }
            tool.validate(permissionCatalog);
        }
        for (PluginDescriptor.WidgetMount mount : descriptor.mounts()) {
            if (mount.requiredPermission() != null && !mount.requiredPermission().isBlank()
                    && !permissionCatalog.exists(mount.requiredPermission())) {
                throw new AuroraException(AuroraErrorCode.PLUGIN_UNKNOWN_PERMISSION,
                        "挂载点声明了不存在的权限码：" + mount.requiredPermission());
            }
        }
        if (!isCompatible(descriptor.minFrameworkVersion())) {
            PluginState state = new PluginState(descriptor, Status.DISABLED,
                    "需要框架版本 ≥ " + descriptor.minFrameworkVersion()
                            + "，当前 " + FRAMEWORK_VERSION + "，插件保持 DISABLED");
            plugins.put(descriptor.pluginId(), state);
            return view(state);
        }
        PluginState state = new PluginState(descriptor, Status.PENDING_AUTH,
                "安装完成，等待管理员授权激活");
        plugins.put(descriptor.pluginId(), state);
        for (ToolDefinition tool : descriptor.tools()) {
            ToolExecutor executor = executors.getOrDefault(tool.name(), new PluginToolPlaceholder(tool));
            toolRegistry.register(tool, executor, false);
        }
        return view(state);
    }

    public synchronized PluginView activate(String pluginId) {
        PluginState state = require(pluginId);
        if (state.status == Status.DISABLED && !isCompatible(state.descriptor.minFrameworkVersion())) {
            throw new AuroraException(AuroraErrorCode.PLUGIN_INCOMPATIBLE, state.reason);
        }
        state.status = Status.ACTIVE;
        state.reason = "已激活";
        toolRegistry.setSourceActive("plugin:" + pluginId, true);
        return view(state);
    }

    public synchronized PluginView disable(String pluginId) {
        PluginState state = require(pluginId);
        state.status = Status.DISABLED;
        state.reason = "管理员已停用";
        toolRegistry.setSourceActive("plugin:" + pluginId, false);
        return view(state);
    }

    public synchronized void uninstall(String pluginId) {
        PluginState state = plugins.remove(pluginId);
        if (state == null) {
            throw new AuroraException(AuroraErrorCode.NOT_FOUND, "插件未安装：" + pluginId);
        }
        toolRegistry.unregisterSource("plugin:" + pluginId);
    }

    public Optional<PluginState> find(String pluginId) {
        return Optional.ofNullable(plugins.get(pluginId));
    }

    public List<PluginView> list() {
        return plugins.values().stream()
                .map(this::view)
                .sorted(Comparator.comparing(PluginView::name))
                .toList();
    }

    public List<PluginDescriptor.WidgetMount> activeMounts(io.aurora.rbac.Principal principal) {
        List<PluginDescriptor.WidgetMount> result = new ArrayList<>();
        plugins.values().stream()
                .filter(p -> p.status == Status.ACTIVE)
                .forEach(p -> p.descriptor.mounts().stream()
                        .filter(m -> principal.can(m.requiredPermission()))
                        .forEach(result::add));
        return result;
    }

    private PluginState require(String pluginId) {
        PluginState state = plugins.get(pluginId);
        if (state == null) {
            throw new AuroraException(AuroraErrorCode.NOT_FOUND, "插件未安装：" + pluginId);
        }
        return state;
    }

    private PluginView view(PluginState state) {
        return new PluginView(state.descriptor.pluginId(), state.descriptor.name(),
                state.descriptor.version(), state.status.name(), state.reason,
                state.descriptor.description(), state.descriptor.vendor(),
                state.descriptor.tools().stream().map(ToolDefinition::name).toList(),
                state.descriptor.mounts(), state.descriptor.minFrameworkVersion());
    }

    private boolean isCompatible(String minimum) {
        if (minimum == null || minimum.isBlank()) {
            return true;
        }
        return compareSemver(minimum, FRAMEWORK_VERSION) <= 0;
    }

    private int compareSemver(String a, String b) {
        String[] left = a.split("\\.");
        String[] right = b.split("\\.");
        int length = Math.max(left.length, right.length);
        for (int i = 0; i < length; i++) {
            int x = i < left.length ? parseInt(left[i]) : 0;
            int y = i < right.length ? parseInt(right[i]) : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value.replaceAll("[^0-9].*$", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Placeholder executor; real plugin executors are wired by the host app. */
    static final class PluginToolPlaceholder implements ToolExecutor {
        private final ToolDefinition definition;

        PluginToolPlaceholder(ToolDefinition definition) {
            this.definition = definition;
        }

        @Override
        public ToolResult execute(ToolExecutionContext context, JsonNode args) {
            throw new AuroraException(AuroraErrorCode.TOOL_NOT_ACTIVE,
                    "插件 Tool 尚未绑定执行器：" + definition.name());
        }
    }

    public static final class PluginState {
        final PluginDescriptor descriptor;
        Status status;
        String reason;

        PluginState(PluginDescriptor descriptor, Status status, String reason) {
            this.descriptor = descriptor;
            this.status = status;
            this.reason = reason;
        }

        public Status status() {
            return status;
        }
    }

    public record PluginView(String pluginId, String name, String version, String status,
                             String reason, String description, String vendor,
                             List<String> tools, List<PluginDescriptor.WidgetMount> mounts,
                             String minFrameworkVersion) {
    }
}
