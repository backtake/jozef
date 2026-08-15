package com.jozef.automation.actions;

import com.jozef.automation.api.DesktopAutomation;
import com.jozef.core.action.Action;
import com.jozef.core.action.ActionContext;
import com.jozef.core.action.ActionResult;
import com.jozef.core.tool.ToolParameters;

import java.util.Map;
import java.util.Set;

/**
 * Closes a running application by name.
 */
public class CloseApplicationAction implements Action {

    private final DesktopAutomation automation;

    public CloseApplicationAction(DesktopAutomation automation) {
        this.automation = automation;
    }

    @Override
    public String name() {
        return "close_application";
    }

    @Override
    public String description() {
        return "Close a running application (e.g. Chrome, Notepad).";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return new ToolParameters()
                .string("name", "Name of the application to close", true)
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
        automation.closeApplication(name);
        return ActionResult.ok("Closed " + name);
    }
}
