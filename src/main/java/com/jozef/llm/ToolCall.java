package com.jozef.llm;

import java.util.Map;

/**
 * A single tool call requested by the LLM.
 */
public record ToolCall(String name, Map<String, Object> arguments) {

    public static ToolCall of(String name, Map<String, Object> arguments) {
        return new ToolCall(name, arguments == null ? Map.of() : arguments);
    }
}
