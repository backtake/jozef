package com.jozef.llm;

import java.util.Map;

/**
 * Schema describing a tool that the LLM can call.
 * This is the MCP-like tool definition sent to the LLM.
 */
public record ToolSchema(
        String name,
        String description,
        Map<String, Object> parameters  // JSON Schema for the parameters
) {
    /**
     * Create a simple tool with no parameters.
     */
    public static ToolSchema simple(String name, String description) {
        return new ToolSchema(name, description,
                Map.of("type", "object", "properties", Map.of()));
    }

    /**
     * Create a tool with parameter properties (simplified JSON Schema).
     */
    public static ToolSchema withParams(String name, String description,
                                         Map<String, Map<String, Object>> paramDefs) {
        return new ToolSchema(name, description,
                Map.of("type", "object", "properties", paramDefs));
    }
}
