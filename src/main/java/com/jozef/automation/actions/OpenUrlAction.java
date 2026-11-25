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
 * Opens a website in the user's normal browser (with their logins), by address
 * or by the name of one of their saved website shortcuts.
 */
public class OpenUrlAction implements Action {

    private final Launcher launcher;
    private final ShortcutStore shortcuts;

    public OpenUrlAction(Launcher launcher, ShortcutStore shortcuts) {
        this.launcher = launcher;
        this.shortcuts = shortcuts;
    }

    @Override
    public String name() {
        return "open_url";
    }

    @Override
    public String description() {
        return "Open a website in the user's own browser (logged-in sessions). Accepts a full address "
                + "(https://...), a domain (allegro.pl) or the name of a saved website shortcut (YouTube, Gmail).";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return new ToolParameters()
                .string("url", "Web address, domain or website shortcut name", true)
                .build();
    }

    @Override
    public Set<String> requiredPermissions() {
        return Set.of("BROWSER");
    }

    @Override
    public ActionResult execute(ActionContext context) {
        String url = context.getString("url");
        if (url == null || url.isBlank()) {
            return ActionResult.fail("No address provided");
        }
        Optional<Shortcut> shortcut = shortcuts == null ? Optional.empty() : shortcuts.find(url);
        String target = shortcut.filter(Shortcut::isWebsite).map(Shortcut::url).orElse(url);
        Launcher.LaunchResult result = launcher.openUrl(target);
        return result.success() ? ActionResult.ok(result.message()) : ActionResult.fail(result.message());
    }
}
