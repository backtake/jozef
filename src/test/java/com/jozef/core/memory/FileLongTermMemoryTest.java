package com.jozef.core.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FileLongTermMemoryTest {

    @TempDir
    Path tempDir;

    @Test
    void remembersAndRecalls() {
        FileLongTermMemory memory = new FileLongTermMemory(tempDir.resolve("m.json").toString());
        MemoryEntry entry = memory.remember("Ważny termin: piątek", List.of("praca"));

        assertThat(entry.id()).isNotBlank();
        assertThat(memory.recent(10)).extracting(MemoryEntry::content).contains("Ważny termin: piątek");
    }

    @Test
    void searchesByContentAndTag() {
        FileLongTermMemory memory = new FileLongTermMemory(tempDir.resolve("m.json").toString());
        memory.remember("Ulubiony kolor to niebieski", List.of("preferencje"));
        memory.remember("Kup mleko", List.of("zakupy"));

        assertThat(memory.search("niebieski")).hasSize(1);
        assertThat(memory.search("zakupy")).hasSize(1);
        assertThat(memory.search("")).hasSize(2);
    }

    @Test
    void forgetsById() {
        FileLongTermMemory memory = new FileLongTermMemory(tempDir.resolve("m.json").toString());
        MemoryEntry entry = memory.remember("do usunięcia", List.of());

        assertThat(memory.forget(entry.id())).isTrue();
        assertThat(memory.forget(entry.id())).isFalse();
        assertThat(memory.all()).isEmpty();
    }

    @Test
    void persistsAcrossInstances() {
        Path file = tempDir.resolve("m.json");
        new FileLongTermMemory(file.toString()).remember("persisted note", List.of("t"));

        FileLongTermMemory reloaded = new FileLongTermMemory(file.toString());
        assertThat(reloaded.recent(5)).extracting(MemoryEntry::content).contains("persisted note");
    }
}
