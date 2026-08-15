package com.jozef.automation.actions;

import com.jozef.automation.api.DesktopAutomation;
import com.jozef.core.action.Action;
import com.jozef.core.action.ActionContext;
import com.jozef.core.action.ActionResult;
import com.jozef.core.tool.ToolParameters;

import java.util.Map;
import java.util.Set;

/**
 * Opens an installed Windows application by name.
 */
public class OpenApplicationAction implements Action {

    private final DesktopAutomation automation;

    public OpenApplicationAction(DesktopAutomation automation) {
        this.automation = automation;
    }

    @Override
    public String name() {
        return "open_application";
    }

    @Override
    public String description() {
        return "Open an installed application (e.g. Chrome, Notepad, Edge, Spotify).";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return new ToolParameters()
                .string("name", "Name of the application to open", true)
                .build();
    }

    @Override
    public Set<String> requiredPermissions() {
        return Set.of("PC_AUTOMATION");
    }

    @Override
    public ActionResult execute(ActionContext context) {
        String name = context.getString("name");
        if (name == null || name.isBlank()) {
            return ActionResult.fail("No application name provided");
        }
        automation.launchApplication(name);
        return ActionResult.ok("Opened " + name);
    }
}
