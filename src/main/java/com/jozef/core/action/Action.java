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

    /**
     * What the user is asked to approve, in their language, with the details that
     * matter ("wysłać e-mail do ania@x.pl, temat: Urlop"). Built from the real
     * arguments — never from model prose. {@code null} = generic summary.
     */
    default String confirmationSummary(Map<String, Object> arguments) {
        return null;
    }

    /**
     * Risk for these specific arguments. Defaults to {@link #strategy()}; actions may
     * raise it (typing text that ends with Enter, a Win-key shortcut). The runtime uses this.
     */
    default ExecutionStrategy strategyFor(Map<String, Object> arguments) {
        return strategy();
    }

    /**
     * Problem with these arguments, or {@code null} when they are fine. Checked before
     * the user is asked to confirm — a call that cannot succeed is never put to the user.
     */
    default String validationError(Map<String, Object> arguments) {
        return null;
    }

    /**
     * True when the result contains text written by third parties (web pages, e-mails,
     * calendar invites). After such a result the rest of the turn is treated as possibly
     * steered by that text.
     */
    default boolean returnsUntrustedContent() {
        return false;
    }

    /**
     * True when this action must be confirmed if third-party text was read earlier in the
     * same turn (saving memory, opening addresses — ways an injected instruction could
     * persist itself or carry data out).
     */
    default boolean confirmAfterUntrustedContent() {
        return false;
    }

    /** Per-tool timeout in seconds; 0 = the agent default. */
    default int timeoutSeconds() {
        return 0;
    }

    /** Generic, honest summary listing every argument (long values shortened). */
    static String defaultConfirmationSummary(String toolName, Map<String, Object> arguments) {
        StringBuilder sb = new StringBuilder("wykonać akcję ").append(toolName);
        if (arguments != null && !arguments.isEmpty()) {
            sb.append(" (");
            int i = 0;
            for (Map.Entry<String, Object> e : new java.util.TreeMap<>(arguments).entrySet()) {
                if (i++ > 0) sb.append(", ");
                String value = String.valueOf(e.getValue());
                if (value.length() > 160) value = value.substring(0, 157) + "…";
                sb.append(e.getKey()).append(": ").append(value);
            }
            sb.append(")");
        }
        return sb.toString();
    }
}
