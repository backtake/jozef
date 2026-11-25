package com.jozef.shortcuts;

import com.jozef.automation.apps.AppMatcher;
import com.jozef.automation.apps.InstalledApp;
import com.jozef.automation.apps.InstalledAppsCatalog;
import com.jozef.automation.launch.Launcher;
import com.jozef.core.agent.FastPath;
import com.jozef.core.util.TextNormalizer;
import com.jozef.llm.ToolCall;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Kilkoma słowami": instant handling of "otwórz / uruchom / włącz / odpal X" when X
 * is one of the user's shortcuts, a spoken web address or an unmistakably named
 * installed program. No model round-trip — answers in milliseconds and works even
 * when the LLM is down. Anything less certain falls through to the model.
 *
 * Deliberately NOT handled here: "pokaż …" (the user usually wants information —
 * "pokaż kalendarz" means "what do I have", not "open the website"), compound
 * requests, and short/partial names that could launch the wrong program.
 */
public class QuickCommands implements FastPath {

    private static final Pattern COMMAND = Pattern.compile(
            "^(?:otworz|uruchom|wlacz|odpal|wejdz na|przejdz na|idz na|open|launch)"
                    + "(?: mi)?(?: (?:strone|stronke|aplikacje|program|apke))?(?: na)? (.+)$");
    private static final Pattern LEADING = Pattern.compile("^(?:hej |hey )?(?:jozefie|jozef)? ?(?:prosze )?");
    private static final Pattern TRAILING = Pattern.compile(" (?:prosze|please|teraz|szybko)$");
    private static final Pattern FILE_LIKE = Pattern.compile(
            "(?i).*\\.(pdf|docx?|xlsx?|pptx?|txt|csv|jpe?g|png|gif|mp3|mp4|mkv|avi|zip|rar|7z|exe|msi|bat|lnk|json|xml|md)$");
    private static final int MIN_APP_QUERY = 4;

    private final ShortcutStore shortcuts;
    private final InstalledAppsCatalog catalog;

    public QuickCommands(ShortcutStore shortcuts, InstalledAppsCatalog catalog, Launcher ignored) {
        this(shortcuts, catalog);
    }

    public QuickCommands(ShortcutStore shortcuts, InstalledAppsCatalog catalog) {
        this.shortcuts = shortcuts;
        this.catalog = catalog;
    }

    @Override
    public Optional<Resolved> resolve(String utterance) {
        Optional<String> target = extractTarget(utterance);
        if (target.isEmpty()) return Optional.empty();
        String x = target.get();

        Optional<Shortcut> shortcut = shortcuts.find(x);
        if (shortcut.isPresent()) {
            Shortcut s = shortcut.get();
            return Optional.of(s.isWebsite()
                    ? new Resolved(ToolCall.of("open_url", Map.of("url", s.name())), "Otwieram " + s.name() + ".")
                    : new Resolved(ToolCall.of("open_application", Map.of("name", s.name())), "Uruchamiam " + s.name() + "."));
        }

        // A spoken domain: "otwórz allegro.pl", "wejdź na wp kropka pl" — but not "raport.pdf".
        String raw = x.replace(" kropka ", ".");
        if (raw.contains(".") && !raw.contains(" ") && !FILE_LIKE.matcher(raw).matches()
                && Launcher.toWebUrl(raw).isPresent()) {
            return Optional.of(new Resolved(ToolCall.of("open_url", Map.of("url", raw)), "Otwieram " + raw + "."));
        }

        // Only an unmistakable installed app: exact name, or a long-enough prefix that
        // covers most of exactly one name.
        if (x.length() < MIN_APP_QUERY) return Optional.empty();
        List<AppMatcher.Match> ranked = AppMatcher.rank(catalog.apps(), x);
        if (ranked.isEmpty()) return Optional.empty();
        AppMatcher.Match top = ranked.get(0);
        boolean unique = ranked.size() == 1 || ranked.get(1).score() < top.score();
        String topName = TextNormalizer.normalize(top.app().name());
        boolean exact = top.score() == 100;
        boolean strongPrefix = top.score() >= 85 && x.length() * 2 >= topName.length();
        if (unique && (exact || strongPrefix)) {
            InstalledApp app = top.app();
            return Optional.of(new Resolved(ToolCall.of("open_application", Map.of("name", app.name())),
                    "Uruchamiam " + app.name() + "."));
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
}
