package com.jozef.core.action;

import java.util.Map;
import java.util.Set;

/**
 * An Action is the ONLY way operations get executed in Jozef.
 *
 * The LLM never touches the machine. It only requests a tool/action by name
 * (plus arguments). The runtime validates permissions and executes the action.
 *
 * Each action also describes itself (name, description, input schema) so the
 * {@link com.jozef.core.tool.ToolRegistry} can expose it to the LLM.
 */
public interface Action {

    /** Unique tool name exposed to the LLM, e.g. "open_application", "type_text". */
    String name();

    /** Human-readable description of what the action does. */
    String description();

    /** JSON-Schema object describing the accepted parameters. */
    default Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    /** Permissions required to run this action (checked by PermissionManager). */
    default Set<String> requiredPermissions() {
        return Set.of();
    }

    /** How risky this action is — drives the confirmation policy. */
    default ExecutionStrategy strategy() {
        return ExecutionStrategy.SAFE;
    }

    /** Execute the action with the given arguments. */
    ActionResult execute(ActionContext context);
}
