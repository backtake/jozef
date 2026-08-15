package com.jozef.core.tool;

import com.jozef.core.action.Action;
import com.jozef.core.action.ExecutionStrategy;

import java.util.Map;
import java.util.Set;

/**
 * A tool definition as exposed to the LLM.
 *
 * The LLM sees this metadata — never the Java implementation details.
 */
public record ToolDefinition(
        String name,
        String description,
        Map<String, Object> inputSchema,
        Set<String> requiredPermissions,
        ExecutionStrategy strategy
) {
    public static ToolDefinition from(Action action) {
        return new ToolDefinition(
                action.name(),
                action.description(),
                action.inputSchema(),
                action.requiredPermissions(),
                action.strategy()
        );
    }
}
