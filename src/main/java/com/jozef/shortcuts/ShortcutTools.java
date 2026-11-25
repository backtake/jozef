package com.jozef.shortcuts;

import com.jozef.automation.launch.Launcher;
import com.jozef.core.action.Action;
import com.jozef.core.action.ActionResult;
import com.jozef.core.action.ExecutionStrategy;
import com.jozef.core.action.LambdaAction;
import com.jozef.core.tool.ToolParameters;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Tools that let the user teach Jozef their own websites and programs.
 *
 * Saving or removing a shortcut changes what "otwórz X" does from then on, without
 * the model — so both need the user's confirmation, and a shortcut created through
 * the model may only point at a web address, an installed app's name or an app
 * protocol ({@code spotify:}). File paths can only come from the user editing
 * the shortcuts file by hand.
 */
public final class ShortcutTools {

    /** Letters (any script), digits, spaces and a few name characters — no paths, no extensions of scripts. */
    private static final Pattern APP_NAME = Pattern.compile("[\\p{L}\\p{N} ._+()&'-]{1,60}");
    private static final Pattern SCRIPT_EXT = Pattern.compile(
            "(?i).*\\.(bat|cmd|ps1|vbs|vbe|js|jse|wsf|wsh|hta|lnk|scr|msi|com|exe|reg|url|pif|cpl)$");

    private ShortcutTools() {}

    public static List<Action> create(ShortcutStore store) {
        return List.of(
                new LambdaAction("save_shortcut",
                        "Add a named shortcut to a WEBSITE or PROGRAM so the user can later say \"otwórz <name>\". "
                                + "Only when the user explicitly asks for a shortcut, e.g. \"dodaj skrót: poczta firmowa "
                                + "to outlook.office.com\". Give either url or app (installed program name, or an app "
                                + "protocol like spotify:). NOT for remembering facts or preferences — use remember_note.",
                        new ToolParameters()
                                .string("name", "Shortcut name the user will say", true)
                                .string("url", "Website address (for websites)", false)
                                .string("app", "Installed program name or app protocol (for applications)", false)
                                .string("aliases", "Optional other spoken names, comma separated", false)
                                .build(),
                        Set.of(), ExecutionStrategy.CONFIRM,
                        ctx -> {
                            Validated v = validate(ctx.arguments());
                            if (v.error() != null) return ActionResult.fail(v.error());
                            Shortcut saved = store.put(v.shortcut());
                            return ActionResult.ok("Saved shortcut '" + saved.name() + "' → " + saved.target());
                        }).withValidation(args -> validate(args).error())
                        .withConfirmationSummary(args -> {
                            Validated v = validate(args);
                            if (v.error() != null) return "zapisać skrót (nieprawidłowy: " + v.error() + ")";
                            Optional<Shortcut> existing = store.find(v.shortcut().name());
                            return "zapisać skrót „" + v.shortcut().name() + "” → " + v.shortcut().target()
                                    + existing.map(e -> " (ZASTĄPI obecny: " + e.target() + ")").orElse("");
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
                        "Remove one of the user's saved shortcuts by name (only when the user asks).",
                        new ToolParameters().string("name", "Shortcut name", true).build(),
                        Set.of(), ExecutionStrategy.CONFIRM,
                        ctx -> store.remove(ctx.getString("name"))
                                ? ActionResult.ok("Removed shortcut " + ctx.getString("name"))
                                : ActionResult.fail("No shortcut named " + ctx.getString("name")))
                        .withConfirmationSummary(args -> store.find(String.valueOf(args.get("name")))
                                .map(s -> "usunąć skrót „" + s.name() + "” → " + s.target())
                                .orElse("usunąć skrót „" + args.get("name") + "” (nie ma takiego)"))
        );
    }

    record Validated(Shortcut shortcut, String error) {
        static Validated error(String message) { return new Validated(null, message); }
    }

    static Validated validate(Map<String, Object> args) {
        String name = str(args.get("name"));
        String url = str(args.get("url"));
        String app = str(args.get("app"));
        if (name.isBlank()) return Validated.error("name is required");
        if (!APP_NAME.matcher(name).matches()) return Validated.error("invalid shortcut name");
        boolean hasUrl = !url.isBlank();
        boolean hasApp = !app.isBlank();
        if (hasUrl == hasApp) return Validated.error("give exactly one of url or app");

        String normalizedUrl = null;
        if (hasUrl) {
            normalizedUrl = Launcher.toWebUrl(url).orElse(null);
            if (normalizedUrl == null) return Validated.error("not a web address: " + url);
        } else {
            boolean protocol = Launcher.isSafeUriScheme(app);
            boolean appName = APP_NAME.matcher(app).matches() && !SCRIPT_EXT.matcher(app).matches();
            if (!protocol && !appName) {
                return Validated.error("app must be an installed program's name or an app protocol like spotify: "
                        + "(file paths can only be added by editing the shortcuts file)");
            }
        }
        String aliases = str(args.get("aliases"));
        List<String> aliasList = aliases.isBlank() ? List.of()
                : Arrays.stream(aliases.split(",")).map(String::trim)
                .filter(s -> !s.isEmpty() && APP_NAME.matcher(s).matches()).toList();
        return new Validated(new Shortcut(name.trim(), aliasList, normalizedUrl, hasApp ? app.trim() : null), null);
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString().trim();
    }
}
