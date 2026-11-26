package com.jozef.automation;

import com.jozef.automation.actions.CloseApplicationAction;
import com.jozef.automation.actions.OpenApplicationAction;
import com.jozef.automation.actions.OpenUrlAction;
import com.jozef.automation.launch.Launcher;
import com.jozef.shortcuts.ShortcutStore;
import com.jozef.shortcuts.ShortcutTools;
import com.jozef.automation.actions.PressKeysAction;
import com.jozef.automation.actions.ScreenshotAction;
import com.jozef.automation.actions.SearchWebAction;
import com.jozef.automation.actions.TypeTextAction;
import com.jozef.automation.actions.TypingMemory;
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
    private final Launcher launcher;
    private final ShortcutStore shortcuts;
    private DesktopAutomation automation;

    public AutomationCapability(AutomationProperties props, ToolRegistry toolRegistry,
                                Launcher launcher, ShortcutStore shortcuts) {
        this.props = props;
        this.toolRegistry = toolRegistry;
        this.launcher = launcher;
        this.shortcuts = shortcuts;
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

        // Opening sites/apps needs no keyboard/mouse control — register it first so it
        // keeps working even where AWT Robot is unavailable.
        toolRegistry.register(new OpenApplicationAction(launcher, shortcuts));
        toolRegistry.register(new OpenUrlAction(launcher, shortcuts));
        toolRegistry.register(new SearchWebAction(launcher));
        ShortcutTools.create(shortcuts).forEach(toolRegistry::register);

        automation = switch (platform.toLowerCase()) {
            case "windows" -> new WindowsAutomationService();
            default -> throw new IllegalArgumentException("Unknown automation platform: " + platform);
        };

        toolRegistry.register(new CloseApplicationAction(automation));
        TypingMemory typing = new TypingMemory();
        toolRegistry.register(new TypeTextAction(automation, typing));
        toolRegistry.register(new PressKeysAction(automation, typing));
        toolRegistry.register(new ScreenshotAction(automation));

        log.info("Automation capability initialized — {} tools registered", toolRegistry.size());
    }

    @Override
    public void shutdown() {
        log.info("Automation capability shut down");
        automation = null;
    }
}
