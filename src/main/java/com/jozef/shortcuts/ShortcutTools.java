package com.jozef.shortcuts;

import com.jozef.automation.launch.Launcher;
import com.jozef.core.action.Action;
import com.jozef.core.action.ActionResult;
import com.jozef.core.action.ExecutionStrategy;
import com.jozef.core.action.LambdaAction;
import com.jozef.core.tool.ToolParameters;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Tools that let the user teach Jozef their own websites and programs. */
public final class ShortcutTools {

    private ShortcutTools() {}

    public static List<Action> create(ShortcutStore store) {
        return List.of(
                new LambdaAction("save_shortcut",
                        "Save a personal shortcut so the user can later say \"otwórz <name>\". "
                                + "Give either url (website) or app (installed program name or .exe path). "
                                + "Use when the user says e.g. \"zapamiętaj, że poczta firmowa to outlook.office.com\".",
                        new ToolParameters()
                                .string("name", "Shortcut name the user will say", true)
                                .string("url", "Website address (for websites)", false)
                                .string("app", "Program name or path (for applications)", false)
                                .string("aliases", "Optional other spoken names, comma separated", false)
                                .build(),
                        Set.of(), ExecutionStrategy.SAFE,
                        ctx -> {
                            String name = ctx.getString("name");
                            String url = ctx.getString("url");
                            String app = ctx.getString("app");
                            if (name == null || name.isBlank()) return ActionResult.fail("name is required");
                            boolean hasUrl = url != null && !url.isBlank();
                            boolean hasApp = app != null && !app.isBlank();
                            if (hasUrl == hasApp) return ActionResult.fail("Give exactly one of url or app");
                            String normalizedUrl = null;
                            if (hasUrl) {
                                normalizedUrl = Launcher.toWebUrl(url).orElse(null);
                                if (normalizedUrl == null) return ActionResult.fail("Not a web address: " + url);
                            }
                            String aliases = ctx.getString("aliases");
                            List<String> aliasList = aliases == null ? List.of()
                                    : Arrays.stream(aliases.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
                            Shortcut saved = store.put(new Shortcut(name.trim(), aliasList, normalizedUrl, hasApp ? app.trim() : null));
                            return ActionResult.ok("Saved shortcut '" + saved.name() + "' → " + saved.target());
                        }),
                new LambdaAction("list_shortcuts",
                        "List the user's saved shortcuts (websites and programs).",
                        null, Set.of(), ExecutionStrategy.SAFE,
                        ctx -> {
                            List<Shortcut> all = store.all();
                            if (all.isEmpty()) return ActionResult.ok("No shortcuts saved.");
                            return ActionResult.ok(all.stream()
                                    .map(s -> s.name() + " → " + s.target())
                                    .collect(Collectors.joining("; ")));
                        }),
                new LambdaAction("remove_shortcut",
                        "Remove one of the user's saved shortcuts by name.",
                        new ToolParameters().string("name", "Shortcut name", true).build(),
                        Set.of(), ExecutionStrategy.SAFE,
                        ctx -> store.remove(ctx.getString("name"))
                                ? ActionResult.ok("Removed shortcut " + ctx.getString("name"))
                                : ActionResult.fail("No shortcut named " + ctx.getString("name")))
        );
    }
}
