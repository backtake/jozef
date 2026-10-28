package com.jozef.google.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jozef.core.util.AtomicFiles;
import lombok.extern.slf4j.Slf4j;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * First-run interactive Google credential setup.
 *
 * <p>When no OAuth client credentials are configured, Jozef prompts the user
 * in the console, opens the Google Cloud Console in the browser, and saves the
 * pasted {@code client_id}/{@code client_secret} to the git-ignored
 * {@code data/} directory. The user never shares these values with anyone.</p>
 */
@Slf4j
public final class GoogleCredentialsSetup {

    /** URL where the user creates an OAuth client (Desktop app). */
    public static final String CONSOLE_URL = "https://console.cloud.google.com/apis/credentials";

    private GoogleCredentialsSetup() {
    }

    /**
     * Prompts interactively for client id/secret. Returns {@code null} when the
     * user skips (blank input) or no console is available.
     */
    public static GoogleCredentialsFile promptInteractive() {
        String clientId = prompt("Google client_id (wklej i Enter; sam Enter = pomiń): ");
        if (clientId == null || clientId.isBlank()) {
            return null;
        }
        String clientSecret = prompt("Google client_secret (wklej i Enter): ");
        if (clientSecret == null || clientSecret.isBlank()) {
            return null;
        }
        return new GoogleCredentialsFile(clientId.trim(), clientSecret.trim());
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

    private static String prompt(String question) {
        java.io.Console console = System.console();
        if (console != null) {
            String line = console.readLine(question);
            return line == null ? null : line.trim();
        }
        // Fallback for IDEs / wrapped processes without a real console.
        System.out.print(question);
        System.out.flush();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            String line = reader.readLine();
            return line == null ? null : line.trim();
        } catch (Exception e) {
            log.warn("Cannot read from console: {}", e.getMessage());
            return null;
        }
    }
}
