package com.jozef.automation;

import com.jozef.automation.actions.CloseApplicationAction;
import com.jozef.automation.actions.OpenApplicationAction;
import com.jozef.automation.actions.PressKeysAction;
import com.jozef.automation.actions.ScreenshotAction;
import com.jozef.automation.actions.SearchWebAction;
import com.jozef.automation.actions.TypeTextAction;
import com.jozef.automation.api.DesktopAutomation;
import com.jozef.automation.windows.WindowsAutomationService;
import com.jozef.configuration.AutomationProperties;
import com.jozef.core.capabilities.Capability;
import com.jozef.core.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * Provides the platform {@link DesktopAutomation} and registers all
 * PC-automation tools in the {@link ToolRegistry}.
 */
@Slf4j
public class AutomationCapability implements Capability {

    private final AutomationProperties props;
    private final ToolRegistry toolRegistry;
    private DesktopAutomation automation;

    public AutomationCapability(AutomationProperties props, ToolRegistry toolRegistry) {
        this.props = props;
        this.toolRegistry = toolRegistry;
    }

    public DesktopAutomation automation() {
        return automation;
    }

    @Override
    public String name() {
        return "automation";
    }

    @Override
    public int priority() {
        return 30;
    }

    @Override
    public void initialize() {
        String platform = props.platform() == null ? "windows" : props.platform();
        log.info("Initializing automation: platform={}", platform);

        automation = switch (platform.toLowerCase()) {
            case "windows" -> new WindowsAutomationService();
            default -> throw new IllegalArgumentException("Unknown automation platform: " + platform);
        };

        toolRegistry.register(new OpenApplicationAction(automation));
        toolRegistry.register(new CloseApplicationAction(automation));
        toolRegistry.register(new TypeTextAction(automation));
        toolRegistry.register(new PressKeysAction(automation));
        toolRegistry.register(new SearchWebAction());
        toolRegistry.register(new ScreenshotAction(automation));

        log.info("Automation capability initialized — {} tools registered", toolRegistry.size());
    }

    @Override
    public void shutdown() {
        log.info("Automation capability shut down");
        automation = null;
    }
}
