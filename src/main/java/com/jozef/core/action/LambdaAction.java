package com.jozef.core.action;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Convenience {@link Action} built from a lambda. Useful for capabilities that
 * register many small tools without creating a class per tool.
 */
public class LambdaAction implements Action {

    private final String name;
    private final String description;
    private final Map<String, Object> inputSchema;
    private final Set<String> requiredPermissions;
    private final ExecutionStrategy strategy;
    private final Function<ActionContext, ActionResult> executor;

    public LambdaAction(String name,
                        String description,
                        Map<String, Object> inputSchema,
                        Set<String> requiredPermissions,
                        ExecutionStrategy strategy,
                        Function<ActionContext, ActionResult> executor) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema == null ? Map.of("type", "object", "properties", Map.of()) : inputSchema;
        this.requiredPermissions = requiredPermissions == null ? Set.of() : requiredPermissions;
        this.strategy = strategy == null ? ExecutionStrategy.SAFE : strategy;
        this.executor = executor;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return inputSchema;
    }

    @Override
    public Set<String> requiredPermissions() {
        return requiredPermissions;
    }

    @Override
    public ExecutionStrategy strategy() {
        return strategy;
    }

    @Override
    public ActionResult execute(ActionContext context) {
        return executor.apply(context);
    }
}
