package com.tracewitness.platform.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class ToolSchemas {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ToolSchemas() {
    }

    public static String reject(String toolName, Map<String, Object> arguments) {
        Path path = schema(toolName);
        if (path == null) {
            return "schema_missing";
        }
        JsonNode schema;
        try {
            schema = MAPPER.readTree(path.toFile());
        } catch (Exception exception) {
            return "schema_unreadable";
        }
        JsonNode properties = schema.path("properties");
        boolean closed = schema.path("additionalProperties").isBoolean() && !schema.path("additionalProperties").booleanValue();
        if (arguments != null && closed) {
            for (String key : arguments.keySet()) {
                if (!properties.has(key)) {
                    return "unexpected_field";
                }
            }
        }
        JsonNode required = schema.path("required");
        if (required.isArray()) {
            for (JsonNode name : required) {
                if (arguments == null || arguments.get(name.asText()) == null) {
                    return "missing_field";
                }
            }
        }
        if (arguments == null) {
            return null;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (!arguments.containsKey(field.getKey()) || arguments.get(field.getKey()) == null) {
                continue;
            }
            String reason = type(field.getValue(), arguments.get(field.getKey()));
            if (reason != null) {
                return reason;
            }
        }
        return null;
    }

    private static String type(JsonNode schema, Object value) {
        String kind = schema.path("type").asText("");
        if ("string".equals(kind)) {
            return value instanceof String ? null : "wrong_type";
        }
        if ("boolean".equals(kind)) {
            return value instanceof Boolean ? null : "wrong_type";
        }
        if ("integer".equals(kind) || "number".equals(kind)) {
            if (!(value instanceof Number) || value instanceof Boolean) {
                return "wrong_type";
            }
            double number = ((Number) value).doubleValue();
            if ("integer".equals(kind) && number != Math.rint(number)) {
                return "wrong_type";
            }
            if (schema.has("minimum") && number < schema.get("minimum").asDouble()) {
                return "out_of_range";
            }
            if (schema.has("maximum") && number > schema.get("maximum").asDouble()) {
                return "out_of_range";
            }
            return null;
        }
        if ("array".equals(kind)) {
            if (!(value instanceof List)) {
                return "wrong_type";
            }
            String itemType = schema.path("items").path("type").asText("");
            if ("string".equals(itemType)) {
                for (Object item : (List<?>) value) {
                    if (!(item instanceof String)) {
                        return "wrong_type";
                    }
                }
            }
            return null;
        }
        return null;
    }

    private static Path schema(String toolName) {
        Path direct = Paths.get("contracts", "tools", toolName + ".schema.json");
        if (Files.exists(direct)) {
            return direct;
        }
        Path parent = Paths.get("..", "contracts", "tools", toolName + ".schema.json");
        if (Files.exists(parent)) {
            return parent;
        }
        return null;
    }
}
