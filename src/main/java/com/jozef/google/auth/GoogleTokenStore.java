package com.jozef.google.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persists OAuth tokens to a JSON file (default {@code data/google-tokens.json}).
 *
 * The data directory is git-ignored, so refresh tokens never reach version control.
 */
@Slf4j
public class GoogleTokenStore {

    private final Path path;
    private final ObjectMapper mapper = new ObjectMapper();

    public GoogleTokenStore(String filePath) {
        String resolved = (filePath == null || filePath.isBlank())
                ? "data/google-tokens.json"
                : filePath;
        this.path = Path.of(resolved);
    }

    public GoogleTokens load() {
        if (!Files.exists(path)) {
            return null;
        }
        try {
            return mapper.readValue(path.toFile(), GoogleTokens.class);
        } catch (Exception e) {
            log.warn("Could not load Google tokens from {}: {}", path, e.getMessage());
            return null;
        }
    }

    public void save(GoogleTokens tokens) {
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), tokens);
            log.info("Google tokens saved to {}", path);
        } catch (Exception e) {
            log.error("Could not save Google tokens to {}: {}", path, e.getMessage(), e);
        }
    }

    public Path path() {
        return path;
    }
}
