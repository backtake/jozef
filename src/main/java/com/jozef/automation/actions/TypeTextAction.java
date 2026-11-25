package com.jozef.automation.actions;

import com.jozef.automation.api.DesktopAutomation;
import com.jozef.core.action.Action;
import com.jozef.core.action.ActionContext;
import com.jozef.core.action.ActionResult;
import com.jozef.core.action.ExecutionStrategy;
import com.jozef.core.tool.ToolParameters;

import java.util.Map;
import java.util.Set;

/**
 * Types text into the currently focused window.
 */
public class TypeTextAction implements Action {

    private final DesktopAutomation automation;

    public TypeTextAction(DesktopAutomation automation) {
        this.automation = automation;
    }

    @Override
    public String name() {
        return "type_text";
    }

    @Override
    public String description() {
        return "Type text into the currently focused window.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return new ToolParameters()
                .string("text", "Text to type", true)
                .build();
    }

    @Override
    public Set<String> requiredPermissions() {
        return Set.of("KEYBOARD_MOUSE");
    }

    /** Text with a line break submits itself (a terminal command, a chat message) — ask first. */
    @Override
    public ExecutionStrategy strategyFor(Map<String, Object> arguments) {
        return hasLineBreak(textOf(arguments)) ? ExecutionStrategy.CONFIRM : ExecutionStrategy.SAFE;
    }

    @Override
    public String confirmationSummary(Map<String, Object> arguments) {
        StringBuilder flat = new StringBuilder();
        for (char c : textOf(arguments).toCharArray()) {
            if (c == '\n') flat.append(" [Enter] ");
            else if (c != '\r') flat.append(c);
        }
        String text = flat.toString();
        return "wpisać w aktywnym oknie (z Enterem): " + (text.length() > 200 ? text.substring(0, 197) + "…" : text);
    }

    private static String textOf(Map<String, Object> arguments) {
        Object raw = arguments == null ? null : arguments.get("text");
        return raw == null ? "" : raw.toString();
    }

    private static boolean hasLineBreak(String text) {
        return text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0;
    }

    @Override
    public ActionResult execute(ActionContext context) {
        String text = context.getString("text");
        if (text == null || text.isEmpty()) {
            return ActionResult.fail("No text provided");
        }
        automation.typeText(text);
        return ActionResult.ok("Typed: " + text);
    }
}
