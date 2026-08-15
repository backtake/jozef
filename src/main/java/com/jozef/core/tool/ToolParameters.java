package com.jozef.core.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fluent builder for JSON-Schema-style tool parameter definitions.
 *
 * <pre>
 *   new ToolParameters()
 *       .string("applicationName", "Name of the application to open", true)
 *       .build();
 * </pre>
 */
public final class ToolParameters {

    private final Map<String, Object> properties = new LinkedHashMap<>();
    private final List<String> required = new ArrayList<>();

    public ToolParameters string(String name, String description) {
        return string(name, description, false);
    }

    public ToolParameters string(String name, String description, boolean required) {
        properties.put(name, Map.of("type", "string", "description", description));
        if (required) this.required.add(name);
        return this;
    }

    public ToolParameters integer(String name, String description, boolean required) {
        properties.put(name, Map.of("type", "integer", "description", description));
        if (required) this.required.add(name);
        return this;
    }

    public ToolParameters bool(String name, String description, boolean required) {
        properties.put(name, Map.of("type", "boolean", "description", description));
        if (required) this.required.add(name);
        return this;
    }

    public Map<String, Object> build() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<>(properties));
        if (!required.isEmpty()) {
            schema.put("required", List.copyOf(required));
        }
        return schema;
    }
}
