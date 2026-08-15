package com.jozef.core.action;

import java.util.Map;

/**
 * The input handed to an {@link Action} when it is executed.
 * Arguments are the tool-call parameters produced by the LLM.
 */
public record ActionContext(Map<String, Object> arguments) {

    public static ActionContext empty() {
        return new ActionContext(Map.of());
    }

    public static ActionContext of(Map<String, Object> arguments) {
        return new ActionContext(arguments == null ? Map.of() : arguments);
    }

    public Object get(String key) {
        return arguments.get(key);
    }

    public String getString(String key) {
        Object value = arguments.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
