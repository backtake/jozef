package com.jozef.core.action;

/**
 * Result of executing an {@link Action}.
 */
public record ActionResult(
        boolean success,
        String message,
        Object data
) {
    public static ActionResult ok(String message) {
        return new ActionResult(true, message, null);
    }

    public static ActionResult ok(String message, Object data) {
        return new ActionResult(true, message, data);
    }

    public static ActionResult fail(String message) {
        return new ActionResult(false, message, null);
    }
}
