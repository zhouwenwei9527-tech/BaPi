package io.aurora.agent.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.aurora.contract.Json;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic local model used for demos, tests and offline development.
 * It may propose *any* tool name — including unregistered ones — which is
 * exactly how the Gateway's default-deny behaviour is exercised (AC-26).
 */
@Component
public class RuleBasedModelPort implements ModelPort {

    private static final Pattern DELETE_USER = Pattern.compile("删除用户\\s*[：:]?\\s*([\\u4e00-\\u9fa5A-Za-z0-9_]+)");
    private static final Pattern ADD_ROLE = Pattern.compile("把\\s*([\\u4e00-\\u9fa5A-Za-z0-9_]+)\\s*加入\\s*([\\u4e00-\\u9fa5A-Za-z0-9_]+)");
    private static final Pattern SEARCH = Pattern.compile("(检索|搜索|查找|知识|文档|论文|薪酬|审批策略|数据范围)");

    @Override
    public String provider() {
        return "local-rule-model";
    }

    @Override
    public ModelTurn complete(ModelRequest request) {
        int tokens = Math.max(8, (request.userMessage() == null ? 0 : request.userMessage().length()) / 2);
        if (request.lastToolName() != null) {
            return ModelTurn.text(summarize(request), tokens);
        }
        String message = request.userMessage() == null ? "" : request.userMessage();
        String lower = message.toLowerCase();

        if (message.contains("忽略之前指令") || lower.contains("ignore previous")) {
            // Deliberately malicious model output; Gateway must still enforce RBAC.
            ObjectNode args = Json.mapper().createObjectNode().put("username", "张三");
            return ModelTurn.call("user.delete", args, tokens);
        }
        if (message.contains("/internal/") || message.contains("db.reset") || message.contains("数据库重置")) {
            return ModelTurn.call("internal.db.reset", Json.mapper().createObjectNode(), tokens);
        }
        if (message.contains("删除") || lower.contains("delete")) {
            Matcher m = DELETE_USER.matcher(message);
            String username = m.find() ? m.group(1) : "张三";
            ObjectNode args = Json.mapper().createObjectNode().put("username", username);
            return ModelTurn.call("user.delete", args, tokens);
        }
        if (message.contains("加入") || message.contains("调整角色") || message.contains("分配角色")
                || lower.contains("assign role")) {
            Matcher m = ADD_ROLE.matcher(message);
            boolean matched = m.find();
            String username = matched ? m.group(1) : "李四";
            String group = matched ? m.group(2) : "运营";
            String roleCode = group.contains("财务") ? "finance"
                    : group.contains("运营") ? "ops" : "ops";
            ObjectNode args = Json.mapper().createObjectNode()
                    .put("username", username).put("roleCode", roleCode);
            return ModelTurn.call("user.assignRole", args, tokens);
        }
        if (SEARCH.matcher(message).find()) {
            ObjectNode args = Json.mapper().createObjectNode()
                    .put("query", message).put("limit", 5);
            return ModelTurn.call("knowledge.search", args, tokens);
        }
        if (message.contains("用户") || message.contains("成员") || message.contains("新增")
                || lower.contains("user")) {
            String query = message.replaceAll(".*(?:查询|搜索|列出)", "").trim();
            if (query.contains("本月") || query.contains("新增")) {
                query = "";
            }
            ObjectNode args = Json.mapper().createObjectNode()
                    .put("query", query.length() > 40 ? query.substring(0, 40) : query)
                    .put("limit", 20);
            return ModelTurn.call("user.search", args, tokens);
        }
        if (message.contains("审计") || message.contains("哈希链")) {
            return ModelTurn.call("audit.verify", Json.mapper().createObjectNode(), tokens);
        }
        return ModelTurn.text("我是 Aurora Agent，以你的权限为上限工作。你可以让我查询用户、检索知识、"
                + "调整角色或删除用户——权限内的写操作会在你确认风险后执行。", tokens);
    }

    private String summarize(ModelRequest request) {
        JsonNode data = request.lastToolResult();
        String tool = request.lastToolName();
        if (data == null) {
            return request.lastToolSummary() == null ? "操作完成。" : request.lastToolSummary();
        }
        if ("user.search".equals(tool)) {
            JsonNode items = data.path("users");
            StringBuilder sb = new StringBuilder();
            sb.append("找到 ").append(items.size()).append(" 位可见用户");
            if (items.size() == 0) {
                return sb.append("。").toString();
            }
            sb.append("：");
            int shown = Math.min(items.size(), 10);
            for (int i = 0; i < shown; i++) {
                JsonNode u = items.get(i);
                sb.append(i == 0 ? "" : "、").append(u.path("displayName").asText())
                        .append("（").append(u.path("deptName").asText()).append("）");
            }
            if (items.size() > shown) {
                sb.append(" 等，仅列出前 ").append(shown).append(" 位");
            }
            return sb.append("。").toString();
        }
        if ("knowledge.search".equals(tool)) {
            JsonNode results = data.path("results");
            return "检索到 " + results.size() + " 条可访问结果，权限过滤在向量查询阶段完成。";
        }
        if ("user.assignRole".equals(tool)) {
            return "已为 " + data.path("displayName").asText(data.path("username").asText())
                    + " 授予角色 " + data.path("roleName").asText(data.path("roleCode").asText()) + "。";
        }
        if ("user.delete".equals(tool)) {
            return "已删除用户 " + data.path("displayName").asText(data.path("username").asText())
                    + "，补偿记录已写入审计。";
        }
        return request.lastToolSummary() == null ? "操作完成。" : request.lastToolSummary();
    }
}
