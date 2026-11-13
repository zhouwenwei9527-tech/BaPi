package io.aurora.app.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.aurora.contract.Json;

import java.util.List;

public final class ToolSupport {

    private ToolSupport() {
    }

    public static JsonNode schema(String json) {
        return Json.parse(json);
    }

    public static ObjectNode object() {
        return Json.mapper().createObjectNode();
    }

    public static ArrayNode array() {
        return Json.mapper().createArrayNode();
    }

    public static <T> ArrayNode arrayOf(List<T> values) {
        ArrayNode array = Json.mapper().createArrayNode();
        values.forEach(v -> array.add(Json.mapper().valueToTree(v)));
        return array;
    }
}
