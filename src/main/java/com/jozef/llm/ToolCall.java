package com.jozef.llm;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A single tool call requested by the LLM.
 *
 * {@code id} is the provider's call id (OpenAI {@code tool_calls[].id}, Anthropic
 * {@code tool_use.id}). Providers that do not issue ids (Ollama) get a locally
 * generated one, so tool results can always be correlated with their call.
 */
public record ToolCall(String id, String name, Map<String, Object> arguments) {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    public ToolCall {
        if (id == null || id.isBlank()) {
            id = "call_" + SEQUENCE.incrementAndGet();
        }
        arguments = arguments == null ? Map.of() : arguments;
    }

    public ToolCall(String name, Map<String, Object> arguments) {
        this(null, name, arguments);
    }

    public static ToolCall of(String name, Map<String, Object> arguments) {
        return new ToolCall(null, name, arguments);
    }

    public static ToolCall of(String id, String name, Map<String, Object> arguments) {
        return new ToolCall(id, name, arguments);
    }
}
