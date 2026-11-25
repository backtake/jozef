package com.jozef.automation.actions;

import com.jozef.automation.launch.Launcher;
import com.jozef.core.action.Action;
import com.jozef.core.action.ActionContext;
import com.jozef.core.action.ActionResult;
import com.jozef.core.tool.ToolParameters;
import com.jozef.shortcuts.Shortcut;
import com.jozef.shortcuts.ShortcutStore;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Opens an application (or one of the user's shortcuts) by name.
 *
 * Resolution order: user shortcut → installed Start-menu app (fuzzy) → explicit
 * path / URI. Ambiguity is reported back so the model can ask the user.
 */
public class OpenApplicationAction implements Action {

    private final Launcher launcher;
    private final ShortcutStore shortcuts;

    public OpenApplicationAction(Launcher launcher, ShortcutStore shortcuts) {
        this.launcher = launcher;
        this.shortcuts = shortcuts;
    }

    @Override
    public String name() {
        return "open_application";
    }

    @Override
    public String description() {
        return "Open an installed application or one of the user's shortcuts by name "
                + "(e.g. Spotify, Discord, Word, Brave, Eksplorator plików, Steam). "
                + "Use the name as the user said it; installed apps are found automatically.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return new ToolParameters()
                .string("name", "Application or shortcut name", true)
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
        Optional<Shortcut> shortcut = shortcuts == null ? Optional.empty() : shortcuts.find(name);
        Launcher.LaunchResult result = shortcut.isPresent()
                ? (shortcut.get().isWebsite() ? launcher.openUrl(shortcut.get().url()) : launcher.openTrusted(shortcut.get().app()))
                : launcher.openApp(name);
        return result.success() ? ActionResult.ok(result.message()) : ActionResult.fail(result.message());
    }
}
