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
 * Presses a key combination, e.g. "ctrl+c", "alt+tab", "enter".
 */
public class PressKeysAction implements Action {

    private final DesktopAutomation automation;

    public PressKeysAction(DesktopAutomation automation) {
        this.automation = automation;
    }

    @Override
    public String name() {
        return "press_keys";
    }

    @Override
    public String description() {
        return "Press a key combination, e.g. ctrl+c, alt+tab, enter, escape.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return new ToolParameters()
                .string("combo", "Key combination to press", true)
                .build();
    }

    @Override
    public Set<String> requiredPermissions() {
        return Set.of("KEYBOARD_MOUSE");
    }

    /**
     * Enter submits whatever was typed (a terminal command, a form) and the Win key
     * opens Run/system UI — the steps that turn keystrokes into actions. Ask first.
     */
    @Override
    public ExecutionStrategy strategyFor(Map<String, Object> arguments) {
        Object raw = arguments == null ? null : arguments.get("combo");
        String combo = raw == null ? "" : raw.toString().toLowerCase().replace(" ", "");
        java.util.List<String> keys = java.util.Arrays.asList(combo.split("\\+"));
        boolean risky = keys.stream().anyMatch(k -> k.equals("win") || k.equals("windows") || k.equals("meta")
                        || k.equals("super") || k.equals("enter") || k.equals("return"))
                || (keys.contains("alt") && keys.contains("f4"))
                || (keys.contains("ctrl") && keys.contains("alt") && (keys.contains("del") || keys.contains("delete")));
        return risky ? ExecutionStrategy.CONFIRM : ExecutionStrategy.SAFE;
    }

    @Override
    public String confirmationSummary(Map<String, Object> arguments) {
        return "nacisnąć klawisze " + (arguments == null ? "" : arguments.get("combo"));
    }

    @Override
    public ActionResult execute(ActionContext context) {
        String combo = context.getString("combo");
        if (combo == null || combo.isEmpty()) {
            return ActionResult.fail("No key combo provided");
        }
        automation.pressKey(combo);
        return ActionResult.ok("Pressed: " + combo);
    }
}
