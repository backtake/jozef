package com.jozef.core.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AtomicFilesTest {

    @TempDir
    Path tempDir;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void writesFileAndLeavesNoTempFiles() throws Exception {
        Path target = tempDir.resolve("nested").resolve("store.json");

        AtomicFiles.write(target, List.of("a", "b"), mapper);

        assertThat(Files.exists(target)).isTrue();
        try (Stream<Path> files = Files.list(target.getParent())) {
            assertThat(files)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .containsExactly("store.json");
        }
    }

    @Test
    void overwritesExistingContent() throws Exception {
        Path target = tempDir.resolve("store.json");
        AtomicFiles.write(target, Map.of("v", 1), mapper);
        AtomicFiles.write(target, Map.of("v", 2), mapper);

        assertThat(Files.readString(target)).contains("\"v\" : 2");
    }
}
