package com.jozef.automation.actions;

import com.jozef.automation.api.DesktopAutomation;
import com.jozef.core.action.Action;
import com.jozef.core.action.ActionContext;
import com.jozef.core.action.ActionResult;
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
