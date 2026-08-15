package com.jozef.core.agent;

import java.time.Instant;
import java.util.Map;

/**
 * A lightweight record of a single tool execution — Jozef's "action memory".
 * This lets the agent answer "what did you just do?".
 */
public record ToolExecution(
        String tool,
        Map<String, Object> arguments,
        boolean success,
        String message,
        long durationMs,
        Instant timestamp
) {
    public static ToolExecution of(String tool, Map<String, Object> arguments,
                                   boolean success, String message, long durationMs) {
        return new ToolExecution(tool, arguments == null ? Map.of() : arguments,
                success, message, durationMs, Instant.now());
    }
}
