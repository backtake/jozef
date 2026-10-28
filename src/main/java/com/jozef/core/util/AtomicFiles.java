package com.jozef.core.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Atomic JSON file writes: serialize to a temp file in the target directory,
 * then atomically move it into place.
 *
 * <p>This prevents a crash mid-write from leaving a truncated/corrupt store
 * (memory, plans, session, tokens). If the filesystem does not support atomic
 * moves, we fall back to a regular replace.</p>
 */
public final class AtomicFiles {

    private AtomicFiles() {
    }

    /**
     * Writes {@code value} as pretty-printed JSON to {@code target}, atomically.
     */
    public static void write(Path target, Object value, ObjectMapper mapper) throws IOException {
        Path absolute = target.toAbsolutePath();
        Path dir = absolute.getParent();
        if (dir != null) {
            Files.createDirectories(dir);
        }

        Path temp = Files.createTempFile(dir, absolute.getFileName().toString(), ".tmp");
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), value);
            try {
                Files.move(temp, absolute,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
