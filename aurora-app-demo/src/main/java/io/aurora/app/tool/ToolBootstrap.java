package io.aurora.app.tool;

import com.fasterxml.jackson.databind.JsonNode;
import io.aurora.contract.Json;
import io.aurora.tool.PluginDescriptor;
import io.aurora.tool.PluginService;
import io.aurora.tool.ToolDefinition;
import io.aurora.tool.ToolExecutor;
import io.aurora.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registers core tools and installs the two demo plugin packages. Plugin tools
 * are registered inactive; only paper-search is explicitly activated.
 */
@Component
@Order(20)
public class ToolBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ToolBootstrap.class);

    private final List<AuroraTool> tools;
    private final ToolRegistry toolRegistry;
    private final PluginService pluginService;

    public ToolBootstrap(List<AuroraTool> tools, ToolRegistry toolRegistry, PluginService pluginService) {
        this.tools = tools;
        this.toolRegistry = toolRegistry;
        this.pluginService = pluginService;
    }

    @Override
    public void run(ApplicationArguments args) {
        Map<String, AuroraTool> byName = new LinkedHashMap<>();
        for (AuroraTool tool : tools) {
            byName.put(tool.definition().name(), tool);
        }
        for (AuroraTool tool : tools) {
            if (ToolDefinition.CORE_SOURCE.equals(tool.definition().source())) {
                toolRegistry.registerCore(tool.definition(), tool);
            }
        }
        log.info("Registered {} core tools", toolRegistry.size());

        PluginDescriptor paper = new PluginDescriptor(
                "paper-search", "文献检索 Tool 包", "1.2.0", "1.0.0",
                "科研文献检索与摘要卡片，演示插件声明式挂载点。",
                "Aurora Labs", "book-open",
                List.of(byName.get("paper.search").definition()),
                List.of(new PluginDescriptor.WidgetMount("palette.results", "UnifiedResult@1",
                        "knowledge:public", render("card", "title", "subtitle"))));
        Map<String, ToolExecutor> paperExecutors = Map.of("paper.search", byName.get("paper.search"));
        try {
            pluginService.install(paper, paperExecutors);
            pluginService.activate("paper-search");
        } catch (RuntimeException e) {
            log.warn("paper-search plugin bootstrap skipped: {}", e.getMessage());
        }

        PluginDescriptor lab = new PluginDescriptor(
                "lab-data", "实验数据导入 Tool 包", "0.9.1", "1.0.0",
                "实验数据集导入能力，默认停留在 PENDING_AUTH，必须人工授权。",
                "Aurora Labs", "flask-conical",
                List.of(byName.get("dataset.import").definition()),
                List.of(new PluginDescriptor.WidgetMount("session.context", "DatasetImport@1",
                        "file:read", render("row", "datasetName", "rows"))));
        Map<String, ToolExecutor> labExecutors = Map.of("dataset.import", byName.get("dataset.import"));
        try {
            pluginService.install(lab, labExecutors);
        } catch (RuntimeException e) {
            log.warn("lab-data plugin bootstrap skipped: {}", e.getMessage());
        }
        log.info("Installed demo plugins: paper-search(ACTIVE), lab-data(PENDING_AUTH)");
    }

    private JsonNode render(String kind, String titleField, String subtitleField) {
        return Json.parse("{\"kind\":\"" + kind + "\",\"titleField\":\"" + titleField
                + "\",\"subtitleField\":\"" + subtitleField + "\"}");
    }
}
