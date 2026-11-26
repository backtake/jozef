package com.jozef.bootstrap;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
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

    /** Tries to take the lock; returns false when another Jozef already holds it. */
    public static synchronized boolean acquire(Path lockFile) {
        try {
            Path parent = lockFile.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            lock = channel.tryLock();
            if (lock == null) {
                channel.close();
                return false;
            }
            return true; // held until the JVM exits
        } catch (IOException | java.nio.channels.OverlappingFileLockException e) {
            return false;
        }
    }
}
