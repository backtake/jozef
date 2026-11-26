package com.jozef.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.assertj.core.api.Assertions.assertThat;

class SingleInstanceTest {

    @Test
    void secondCopyCannotTakeTheLock(@TempDir Path dir) throws Exception {
        Path lockFile = dir.resolve("data").resolve("jozef.lock");
        // Simulate the first, running Jozef holding the lock (another channel = another process view).
        java.nio.file.Files.createDirectories(lockFile.getParent());
        try (FileChannel first = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var held = first.lock()) {
            assertThat(SingleInstance.acquire(lockFile)).isFalse();
        }
    }
}
