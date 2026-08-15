package com.jozef.automation.actions;

import com.jozef.automation.api.DesktopAutomation;
import com.jozef.core.action.Action;
import com.jozef.core.action.ActionContext;
import com.jozef.core.action.ActionResult;

import java.util.Map;
import java.util.Set;

/**
 * Captures a screenshot of the primary screen.
 */
public class ScreenshotAction implements Action {

    private final DesktopAutomation automation;

    public ScreenshotAction(DesktopAutomation automation) {
        this.automation = automation;
    }

    @Override
    public String name() {
        return "take_screenshot";
    }

    @Override
    public String description() {
        return "Capture a screenshot of the screen and save it to a file.";
    }

    @Override
    public Set<String> requiredPermissions() {
        return Set.of("SCREEN");
    }

    @Override
    public ActionResult execute(ActionContext context) {
        try {
            String path = automation.takeScreenshot();
            return ActionResult.ok("Screenshot saved to " + path, path);
        } catch (Exception e) {
            return ActionResult.fail("Screenshot failed: " + e.getMessage());
        }
    }
}
