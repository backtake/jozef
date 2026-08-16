package com.jozef.speech.vosk;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Extracts Vosk's native libraries and points JNA at them.
 *
 * Vosk bundles its {@code .dll/.so/.dylib} inside the jar under platform
 * directories. Inside a Spring Boot fat jar the default extraction can fail,
 * so we extract to {@code data/native/vosk} ourselves and set {@code jna.library.path}.
 */
@Slf4j
public final class VoskNativeLibrary {

    private static volatile boolean prepared = false;

    private VoskNativeLibrary() {}

    /** Extract native libraries and register their directory with JNA. Idempotent. */
    public static synchronized void prepare() {
        if (prepared) {
            return;
        }
        Path dir = Path.of(System.getProperty("user.dir"), "data", "native", "vosk");
        try {
            Files.createDirectories(dir);
            for (String resource : nativeResources()) {
                String fileName = resource.substring(resource.lastIndexOf('/') + 1);
                Path target = dir.resolve(fileName);
                try (InputStream in = VoskNativeLibrary.class.getClassLoader().getResourceAsStream(resource)) {
                    if (in == null) {
                        log.debug("Vosk native resource not found: {}", resource);
                        continue;
                    }
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            String existing = System.getProperty("jna.library.path", "");
            String updated = dir.toAbsolutePath()
                    + (existing.isBlank() ? "" : File.pathSeparator + existing);
            System.setProperty("jna.library.path", updated);
            prepared = true;
            log.info("Prepared Vosk native libraries in {}", dir.toAbsolutePath());
        } catch (IOException e) {
            log.warn("Could not extract Vosk native libraries: {}", e.getMessage());
        }
    }

    static List<String> nativeResources() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return List.of(
                    "win32-x86-64/libvosk.dll",
                    "win32-x86-64/libstdc++-6.dll",
                    "win32-x86-64/libwinpthread-1.dll",
                    "win32-x86-64/libgcc_s_seh-1.dll");
        }
        if (os.contains("linux")) {
            return List.of("linux-x86-64/libvosk.so");
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return List.of("darwin/libvosk.dylib");
        }
        return List.of();
    }
}
