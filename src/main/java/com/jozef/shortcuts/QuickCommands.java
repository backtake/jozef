package com.jozef.shortcuts;

import com.jozef.automation.apps.AppMatcher;
import com.jozef.automation.apps.InstalledApp;
import com.jozef.automation.apps.InstalledAppsCatalog;
import com.jozef.automation.launch.Launcher;
import com.jozef.core.agent.FastPath;
import com.jozef.core.agent.ToolExecution;
import com.jozef.core.util.TextNormalizer;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Kilkoma słowami": instant handling of "otwórz / uruchom / włącz / odpal X"
 * when X is one of the user's shortcuts, a web address or an exactly-named
 * installed program. No model round-trip — answers in milliseconds and works
 * even when the LLM is down. Anything less certain falls through to the model.
 */
@Slf4j
public class QuickCommands implements FastPath {

    private static final Pattern COMMAND = Pattern.compile(
            "^(?:otworz|uruchom|wlacz|odpal|pokaz|wejdz na|przejdz na|idz na|open|launch|start)"
                    + "(?: mi)?(?: (?:strone|stronke|aplikacje|program|apke))?(?: na)? (.+)$");
    private static final Pattern LEADING = Pattern.compile("^(?:hej |hey )?(?:jozefie|jozef)? ?(?:prosze )?");
    private static final Pattern TRAILING = Pattern.compile(" (?:prosze|please|teraz|szybko)$");

    private final ShortcutStore shortcuts;
    private final InstalledAppsCatalog catalog;
    private final Launcher launcher;

    public QuickCommands(ShortcutStore shortcuts, InstalledAppsCatalog catalog, Launcher launcher) {
        this.shortcuts = shortcuts;
        this.catalog = catalog;
        this.launcher = launcher;
    }

    @Override
    public Optional<Handled> tryHandle(String utterance) {
        Optional<String> target = extractTarget(utterance);
        if (target.isEmpty()) return Optional.empty();
        String x = target.get();

        Optional<Shortcut> shortcut = shortcuts.find(x);
        if (shortcut.isPresent()) {
            Shortcut s = shortcut.get();
            Launcher.LaunchResult r = s.isWebsite() ? launcher.openUrl(s.url()) : launcher.openApp(s.app());
            return Optional.of(handled(r, s.isWebsite() ? "open_url" : "open_application", s.name(), s.isWebsite()));
        }

        // A spoken domain: "otwórz allegro.pl", "wejdź na wp.pl".
        String raw = x.replace(" kropka ", ".");
        if (Launcher.toWebUrl(raw).isPresent() && raw.contains(".")) {
            return Optional.of(handled(launcher.openUrl(raw), "open_url", raw, true));
        }

        // Only an unmistakable installed app — exact name or the start of exactly one name.
        List<AppMatcher.Match> ranked = AppMatcher.rank(catalog.apps(), x);
        if (!ranked.isEmpty() && ranked.get(0).score() >= 85
                && (ranked.size() == 1 || ranked.get(1).score() < ranked.get(0).score())) {
            InstalledApp app = ranked.get(0).app();
            return Optional.of(handled(launcher.openApp(app.name()), "open_application", app.name(), false));
        }
        return Optional.empty();
    }

    static Optional<String> extractTarget(String utterance) {
        String n = TextNormalizer.normalize(utterance);
        n = LEADING.matcher(n).replaceFirst("");
        n = TRAILING.matcher(n).replaceFirst("");
        Matcher m = COMMAND.matcher(n);
        if (!m.matches()) return Optional.empty();
        String target = m.group(1).trim();
        // Compound requests ("otwórz YouTube i puść muzykę") need the model.
        if (target.isEmpty() || target.contains(" i ") || target.contains(" oraz ") || target.split(" ").length > 4) {
            return Optional.empty();
        }
        return Optional.of(target);
    }

    private static Handled handled(Launcher.LaunchResult r, String tool, String name, boolean website) {
        String reply = r.success()
                ? (website ? "Otwieram " : "Uruchamiam ") + (r.resolvedName() != null && !website ? r.resolvedName() : name) + "."
                : "Nie udało się otworzyć " + name + ".";
        return new Handled(reply, ToolExecution.of(tool, Map.of("name", name), r.success(), r.message(), 0));
    }
}
