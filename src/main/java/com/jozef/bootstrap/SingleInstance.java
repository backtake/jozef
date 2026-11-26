package com.jozef.bootstrap;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Guarantees one Jozef per data folder. Two copies would both hear the microphone,
 * both run every command and both execute an approved action ("tak" → two e-mails),
 * and their Telegram pollers would knock each other off.
 */
public final class SingleInstance {

    private static FileChannel channel;
    private static FileLock lock;

    private SingleInstance() {}

    /**
     * Tries to take the lock; returns false when another Jozef already holds it.
     *
     * @throws IllegalStateException when the lock file cannot be created at all (e.g. the
     *                               folder is not writable) — a different problem than
     *                               "already running", reported as such
     */
    public static synchronized boolean acquire(Path lockFile) {
        try {
            Path parent = lockFile.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new IllegalStateException("Nie mogę utworzyć " + lockFile + ": " + e.getMessage(), e);
        }
        try {
            lock = channel.tryLock();
        } catch (OverlappingFileLockException e) {
            lock = null;
        } catch (IOException e) {
            throw new IllegalStateException("Nie mogę zablokować " + lockFile + ": " + e.getMessage(), e);
        }
        if (lock == null) {
            try {
                channel.close();
            } catch (IOException ignored) {
                // closing a channel we could not lock
            }
            return false;
        }
        return true; // held until the JVM exits
    }
}
