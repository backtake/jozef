package com.jozef.automation.actions;

import com.jozef.automation.api.DesktopAutomation;
import com.jozef.core.action.Action;
import com.jozef.core.action.ActionContext;
import com.jozef.core.action.ActionResult;
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
