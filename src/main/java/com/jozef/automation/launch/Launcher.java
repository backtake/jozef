package com.jozef.automation.launch;

import com.jozef.automation.apps.AppMatcher;
import com.jozef.automation.apps.InstalledApp;
import com.jozef.automation.apps.InstalledAppsCatalog;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Opens things for the user the way they would themselves: websites in their
 * normal (logged-in) browser, programs from the Start menu.
 *
 * Nothing goes through {@code cmd /c}: targets are handed to the OS opener as a
 * single argument, so model- or user-supplied text cannot inject commands.
 */
@Slf4j
public class Launcher {

    /** Launches a single target with the OS. */
    public interface SystemOpener {
        void open(String target) throws Exception;

        /** Opens {@code url} with a specific browser executable. */
        void openWith(String executable, String url) throws Exception;
    }

    public record LaunchResult(boolean success, String message, String resolvedName) {
        static LaunchResult ok(String message, String name) { return new LaunchResult(true, message, name); }
        static LaunchResult fail(String message) { return new LaunchResult(false, message, null); }
    }

    private static final Pattern DOMAIN = Pattern.compile("^[a-z0-9-]+(\\.[a-z0-9-]+)*\\.[a-z]{2,}(/\\S*)?$");
    private static final Pattern URI_SCHEME_APP = Pattern.compile("^[a-z][a-z0-9+.-]{1,30}:$");

    private final InstalledAppsCatalog catalog;
    private final SystemOpener opener;
    private final String browserExecutable;

    public Launcher(InstalledAppsCatalog catalog, SystemOpener opener, String browserExecutable) {
        this.catalog = catalog;
        this.opener = opener;
        this.browserExecutable = browserExecutable == null || browserExecutable.isBlank() ? null : browserExecutable;
    }

    /** Normalises "youtube.com" / "https://…" into an http(s) URL, or empty when it is not a web address. */
    public static Optional<String> toWebUrl(String text) {
        if (text == null) return Optional.empty();
        String t = text.trim();
        String lower = t.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            try {
                URI uri = URI.create(t);
                return uri.getHost() == null ? Optional.empty() : Optional.of(uri.toString());
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
        if (lower.startsWith("www.") || DOMAIN.matcher(lower).matches()) {
            return toWebUrl("https://" + t);
        }
        return Optional.empty();
    }

    /** Opens a website in the user's browser. Only http(s) is allowed. */
    public LaunchResult openUrl(String url) {
        Optional<String> web = toWebUrl(url);
        if (web.isEmpty()) {
            return LaunchResult.fail("Not a web address (only http/https are allowed): " + url);
        }
        try {
            if (browserExecutable != null) {
                opener.openWith(browserExecutable, web.get());
            } else {
                opener.open(web.get());
            }
            return LaunchResult.ok("Opened " + web.get(), web.get());
        } catch (Exception e) {
            log.warn("Cannot open {}: {}", web.get(), e.getMessage());
            return LaunchResult.fail("Cannot open " + web.get() + ": " + e.getMessage());
        }
    }

    /**
     * Opens an application by spoken name ("discord", "eksplorator plików"),
     * an explicit target ({@code C:\…\app.exe}, {@code spotify:}) or a web address.
     */
    public LaunchResult openApp(String nameOrTarget) {
        if (nameOrTarget == null || nameOrTarget.isBlank()) {
            return LaunchResult.fail("No application name given");
        }
        String target = nameOrTarget.trim();

        if (toWebUrl(target).isPresent() && !target.toLowerCase(Locale.ROOT).endsWith(".exe")) {
            return openUrl(target);
        }
        if (URI_SCHEME_APP.matcher(target.toLowerCase(Locale.ROOT)).matches() || looksLikePath(target)) {
            return launch(target, target);
        }

        Optional<InstalledApp> app = AppMatcher.best(catalog.apps(), target);
        if (app.isEmpty()) {
            catalog.refresh(); // maybe installed since the last scan
            app = AppMatcher.best(catalog.apps(), target);
        }
        if (app.isPresent()) {
            return launch("shell:AppsFolder\\" + app.get().appId(), app.get().name());
        }

        List<AppMatcher.Match> candidates = AppMatcher.rank(catalog.apps(), target);
        if (!candidates.isEmpty()) {
            String names = candidates.stream().limit(4).map(m -> m.app().name()).toList().toString();
            return LaunchResult.fail("Ambiguous application '" + target + "'. Candidates: " + names);
        }
        return LaunchResult.fail("No installed application matches '" + target + "'");
    }

    private LaunchResult launch(String target, String displayName) {
        try {
            opener.open(target);
            log.info("Launched {} ({})", displayName, target);
            return LaunchResult.ok("Opened " + displayName, displayName);
        } catch (Exception e) {
            log.warn("Cannot launch {}: {}", target, e.getMessage());
            return LaunchResult.fail("Cannot open " + displayName + ": " + e.getMessage());
        }
    }

    private static boolean looksLikePath(String s) {
        // Local paths and shell: ids only — never UNC (\\server\share\x.exe) network paths.
        return s.matches("^[a-zA-Z]:\\\\.*") || s.startsWith("shell:");
    }

    /** Windows: {@code explorer.exe <target>} — opens URLs, apps, files and shell: ids without a shell. */
    public static SystemOpener windowsOpener() {
        return new SystemOpener() {
            @Override
            public void open(String target) throws Exception {
                new ProcessBuilder("explorer.exe", target).start();
            }

            @Override
            public void openWith(String executable, String url) throws Exception {
                new ProcessBuilder(executable, url).start();
            }
        };
    }
}
