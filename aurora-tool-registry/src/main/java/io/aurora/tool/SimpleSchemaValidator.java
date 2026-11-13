package io.aurora.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Small, strict JSON Schema subset validator. Covers the schema vocabulary
 * used by registered tools without pulling an extra runtime dependency.
 */
public final class SimpleSchemaValidator {

    private SimpleSchemaValidator() {
    }

    public static List<String> errors(JsonNode schema, JsonNode value) {
        List<String> errors = new ArrayList<>();
        validate(schema, value, "$", errors);
        return errors;
    }

    private static void validate(JsonNode schema, JsonNode value, String path, List<String> errors) {
        if (schema == null || schema.isMissingNode() || !schema.isObject()) {
            return;
        }
        String type = schema.path("type").asText(null);
        if (type != null && !matchesType(type, value)) {
            errors.add(path + " 期望类型 " + type);
            return;
        }
        if (schema.has("enum")) {
            boolean found = false;
            for (JsonNode allowed : schema.get("enum")) {
                if (allowed.equals(value)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                errors.add(path + " 不在枚举范围内");
            }
        }
        if (value.isTextual()) {
            String text = value.asText();
            if (schema.has("minLength") && text.length() < schema.get("minLength").asInt()) {
                errors.add(path + " 长度小于 " + schema.get("minLength").asInt());
            }
            if (schema.has("maxLength") && text.length() > schema.get("maxLength").asInt()) {
                errors.add(path + " 长度大于 " + schema.get("maxLength").asInt());
            }
            if (schema.has("pattern")) {
                try {
                    if (!Pattern.compile(schema.get("pattern").asText()).matcher(text).find()) {
                        errors.add(path + " 不匹配 pattern");
                    }
                } catch (PatternSyntaxException e) {
                    errors.add(path + " 的 pattern 无效");
                }
            }
        }
        if (value.isNumber()) {
            if (schema.has("minimum") && value.asDouble() < schema.get("minimum").asDouble()) {
                errors.add(path + " 小于 minimum");
            }
            if (schema.has("maximum") && value.asDouble() > schema.get("maximum").asDouble()) {
                errors.add(path + " 大于 maximum");
            }
        }
        if (value.isObject()) {
            JsonNode required = schema.path("required");
            if (required.isArray()) {
                for (JsonNode field : required) {
                    if (!value.has(field.asText())) {
                        errors.add(path + " 缺少必填字段 " + field.asText());
                    }
                }
            }
            JsonNode properties = schema.path("properties");
            if (properties.isObject()) {
                Iterator<String> names = properties.fieldNames();
                while (names.hasNext()) {
                    String name = names.next();
                    if (value.has(name)) {
                        validate(properties.get(name), value.get(name), path + "." + name, errors);
                    }
                }
            }
            if (schema.path("additionalProperties").isBoolean()
                    && !schema.path("additionalProperties").asBoolean()
                    && properties.isObject()) {
                Iterator<String> fieldNames = value.fieldNames();
                while (fieldNames.hasNext()) {
                    String field = fieldNames.next();
                    if (!properties.has(field)) {
                        errors.add(path + " 不允许字段 " + field);
                    }
                }
            }
        }
        if (value.isArray() && schema.has("items")) {
            ArrayNode array = (ArrayNode) value;
            for (int i = 0; i < array.size(); i++) {
                validate(schema.get("items"), array.get(i), path + "[" + i + "]", errors);
            }
        }
        if (schema.has("default") && value.isMissingNode()) {
            errors.add(path + " 缺失");
        }
        if (schema.has("oneOf") && schema.get("oneOf").isArray()) {
            long matched = 0;
            for (JsonNode option : schema.get("oneOf")) {
                List<String> optionErrors = new ArrayList<>();
                validate(option, value, path, optionErrors);
                if (optionErrors.isEmpty()) {
                    matched++;
                }
            }
            if (matched != 1) {
                errors.add(path + " 不满足 oneOf 约束");
            }
        }
    }

    private static boolean matchesType(String type, JsonNode value) {
        return switch (type) {
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "string" -> value.isTextual();
            case "number" -> value.isNumber();
            case "integer" -> value.isIntegralNumber();
            case "boolean" -> value.isBoolean();
            case "null" -> value.isNull();
            default -> true;
        };
    }

    public static ObjectNode objectSchema() {
        return io.aurora.contract.Json.mapper().createObjectNode().put("type", "object");
    }
}
