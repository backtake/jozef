package com.jozef.google.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jozef.core.util.AtomicFiles;
import lombok.extern.slf4j.Slf4j;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Automatic first-run Google credential setup.
 *
 * <p>Jozef opens the Google Cloud Console in the browser, then watches the
 * Downloads/data directories for the {@code client_secret*.json} file that
 * Google downloads when the user creates a "Desktop app" OAuth client. The
 * user never has to type anything into Jozef.</p>
 */
@Slf4j
public final class GoogleCredentialsSetup {

    /** URL where the user creates an OAuth client (Desktop app). */
    public static final String CONSOLE_URL = "https://console.cloud.google.com/apis/credentials";

    private GoogleCredentialsSetup() {
    }

    /**
     * Polls the Downloads/Desktop/data directories for a Google-downloaded
     * {@code client_secret*.json} file and returns the credentials from the
     * first valid one found. Returns {@code null} on timeout.
     */
    public static GoogleCredentialsFile findDownloadedCredentials(long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            for (Path dir : candidateDirs()) {
                GoogleCredentialsFile creds = scanForClientSecret(dir);
                if (creds != null) {
                    log.info("Znaleziono dane logowania Google w {}", dir);
                    return creds;
                }
            }
            sleep(2000);
        }
        return null;
    }

    /** Persists credentials as a flat JSON file (git-ignored). */
    public static void save(String filePath, GoogleCredentialsFile creds) {
        try {
            Map<String, String> map = new LinkedHashMap<>();
            map.put("client_id", creds.clientId());
            map.put("client_secret", creds.clientSecret());
            AtomicFiles.write(Path.of(filePath), map, new ObjectMapper());
            log.info("Google credentials saved to {}", filePath);
        } catch (Exception e) {
            log.error("Could not save Google credentials to {}: {}", filePath, e.getMessage(), e);
        }
    }

    /** Best-effort open of the Google Cloud Console credentials page. */
    public static void openConsoleInBrowser() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(CONSOLE_URL));
                return;
            }
        } catch (Exception e) {
            log.warn("Could not open the browser automatically: {}", e.getMessage());
        }
        log.info("Otwórz ręcznie: {}", CONSOLE_URL);
    }

    // ─── Scanning ──────────────────────────────────────────────

    private static List<Path> candidateDirs() {
        List<Path> dirs = new ArrayList<>();
        dirs.add(Path.of("data"));
        String home = System.getProperty("user.home");
        if (home != null && !home.isBlank()) {
            dirs.add(Path.of(home, "Downloads"));
            dirs.add(Path.of(home, "Desktop"));
        }
        dirs.add(Path.of("."));
        return dirs;
    }

    static GoogleCredentialsFile scanForClientSecret(Path dir) {
        if (!Files.isDirectory(dir)) {
            return null;
        }
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String name = file.getFileName().toString().toLowerCase();
                if (name.startsWith("client_secret") && name.endsWith(".json")) {
                    GoogleCredentialsFile creds = GoogleCredentialsFile.load(file.toString());
                    if (creds != null) {
                        return creds;
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Cannot scan {} for Google credentials: {}", dir, e.getMessage());
        }
        return null;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
