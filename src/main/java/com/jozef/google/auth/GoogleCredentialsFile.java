package com.jozef.google.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Reads OAuth client credentials from the JSON file downloaded from Google
 * Cloud Console ("OAuth client ID" → "Desktop app").
 *
 * <p>Supported shapes (as downloaded by Google):</p>
 * <pre>
 * { "installed": { "client_id": "...", "client_secret": "...", ... } }
 * { "web":       { "client_id": "...", "client_secret": "...", ... } }
 * { "client_id": "...", "client_secret": "..." }   // flat convenience form
 * </pre>
 *
 * The user creates this file themselves on their own machine — it is never
 * shared with anyone and lives in the git-ignored {@code data/} directory.
 */
public record GoogleCredentialsFile(String clientId, String clientSecret) {

    /**
     * Loads credentials from {@code filePath}; returns {@code null} when the
     * file is missing or does not contain a client id + secret.
     */
    public static GoogleCredentialsFile load(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return null;
        }
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            return null;
        }
        try {
            Map<String, Object> json = new ObjectMapper()
                    .readValue(path.toFile(), new TypeReference<>() {});

            for (String section : new String[]{"installed", "web"}) {
                Object nested = json.get(section);
                if (nested instanceof Map<?, ?> m) {
                    String id = str(m.get("client_id"));
                    String secret = str(m.get("client_secret"));
                    if (present(id) && present(secret)) {
                        return new GoogleCredentialsFile(id, secret);
                    }
                }
            }

            String id = str(json.get("client_id"));
            String secret = str(json.get("client_secret"));
            if (present(id) && present(secret)) {
                return new GoogleCredentialsFile(id, secret);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
