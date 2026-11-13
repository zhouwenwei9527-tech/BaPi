package io.aurora.tool;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Set;

/**
 * Describe-only plugin package (PRD §7.3). Plugins never ship arbitrary JS;
 * the shell renders their declarations with its own components.
 */
public record PluginDescriptor(
        String pluginId,
        String name,
        String version,
        String minFrameworkVersion,
        String description,
        String vendor,
        String icon,
        List<ToolDefinition> tools,
        List<WidgetMount> mounts
) {

    public static final Set<String> MOUNT_POINTS = Set.of(
            "desktop.icons", "palette.results", "finder.sidebar", "session.context");

    public PluginDescriptor {
        tools = tools == null ? List.of() : List.copyOf(tools);
        mounts = mounts == null ? List.of() : List.copyOf(mounts);
    }

    public record WidgetMount(String mountPoint, String dataContract, String requiredPermission,
                              JsonNode render) {
        public WidgetMount {
            if (!MOUNT_POINTS.contains(mountPoint)) {
                throw new IllegalArgumentException("未知挂载点：" + mountPoint);
            }
        }
    }
}
