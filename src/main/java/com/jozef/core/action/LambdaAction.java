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
    private Function<Map<String, Object>, String> confirmationSummary;
    private Function<Map<String, Object>, ExecutionStrategy> dynamicStrategy;
    private Function<Map<String, Object>, String> validation;
    private int timeoutSeconds;

    /** Human summary shown when the user must confirm this action (built from real arguments). */
    public LambdaAction withConfirmationSummary(Function<Map<String, Object>, String> summary) {
        this.confirmationSummary = summary;
        return this;
    }

    /** Risk that depends on the arguments (e.g. typing text that contains Enter). */
    public LambdaAction withStrategyFor(Function<Map<String, Object>, ExecutionStrategy> strategyFor) {
        this.dynamicStrategy = strategyFor;
        return this;
    }

    /** Argument check run before asking the user to confirm (no point confirming a broken call). */
    public LambdaAction withValidation(Function<Map<String, Object>, String> validation) {
        this.validation = validation;
        return this;
    }

    @Override
    public String validationError(Map<String, Object> arguments) {
        return validation == null ? null : validation.apply(arguments == null ? Map.of() : arguments);
    }

    public LambdaAction withTimeoutSeconds(int seconds) {
        this.timeoutSeconds = seconds;
        return this;
    }

    @Override
    public String confirmationSummary(Map<String, Object> arguments) {
        return confirmationSummary == null ? null : confirmationSummary.apply(arguments == null ? Map.of() : arguments);
    }

    @Override
    public ExecutionStrategy strategyFor(Map<String, Object> arguments) {
        if (dynamicStrategy == null) return strategy();
        ExecutionStrategy s = dynamicStrategy.apply(arguments == null ? Map.of() : arguments);
        // Arguments may only make an action riskier, never safer than declared.
        return s == null || s.ordinal() < strategy().ordinal() ? strategy() : s;
    }

    @Override
    public int timeoutSeconds() {
        return timeoutSeconds;
    }

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
