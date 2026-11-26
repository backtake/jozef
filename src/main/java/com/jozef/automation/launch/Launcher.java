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
     * Opens an application by spoken name ("discord", "eksplorator plików"), a bare
     * URI scheme ({@code spotify:}) or a web address. Model-supplied input: file
     * paths and {@code shell:} targets are refused — a path could point at a script.
     */
    public LaunchResult openApp(String nameOrTarget) {
        return openApp(nameOrTarget, false);
    }

    /**
     * Like {@link #openApp(String)} but also accepts local paths ({@code C:\Games\x.exe})
     * and {@code shell:} ids. Only for targets the user wrote themselves (shortcuts file).
     */
    public LaunchResult openTrusted(String nameOrTarget) {
        return openApp(nameOrTarget, true);
    }

    private LaunchResult openApp(String nameOrTarget, boolean allowPaths) {
        if (nameOrTarget == null || nameOrTarget.isBlank()) {
            return LaunchResult.fail("No application name given");
        }
        String target = nameOrTarget.trim();

        if (looksLikePath(target) || target.startsWith("\\\\")) {
            return allowPaths && !target.startsWith("\\\\")
                    ? launch(target, target)
                    : LaunchResult.fail("Opening files or paths is not allowed here — name an installed application");
        }
        if (toWebUrl(target).isPresent() && !target.toLowerCase(Locale.ROOT).endsWith(".exe")) {
            return openUrl(target);
        }
        if (isSafeUriScheme(target) || (allowPaths && URI_SCHEME_APP.matcher(target.toLowerCase(Locale.ROOT)).matches()
                && !target.equalsIgnoreCase("file:"))) {
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
        return s.matches("^[a-zA-Z]:[\\\\/].*") || s.startsWith("shell:") || s.contains("\\") || s.contains("/")
                && !s.toLowerCase(Locale.ROOT).startsWith("http");
    }

    /**
     * App protocols the model may launch ("spotify:", "ms-settings:"). An allowlist: unknown
     * handlers (search-ms:, ms-msdt:, custom installers) have been used for attacks. The
     * user can still put any protocol in their own shortcuts file.
     */
    private static final java.util.Set<String> SAFE_SCHEMES = java.util.Set.of(
            "spotify:", "ms-settings:", "steam:", "discord:", "msteams:", "zoommtg:", "slack:", "tg:",
            "whatsapp:", "mailto:", "ms-photos:", "ms-clock:", "calculator:", "ms-calculator:", "ms-screenclip:",
            "ms-windows-store:", "xbox:", "ms-gamebar:", "outlookcal:", "outlookmail:", "onenote:");

    public static boolean isSafeUriScheme(String target) {
        return target != null && SAFE_SCHEMES.contains(target.trim().toLowerCase(Locale.ROOT));
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
