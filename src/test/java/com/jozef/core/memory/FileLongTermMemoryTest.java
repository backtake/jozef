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

    @Test
    void remembersWithDateAndFiltersByDate() {
        FileLongTermMemory memory = new FileLongTermMemory(tempDir.resolve("m.json").toString());
        memory.remember("Plan na dziś: zakupy", List.of(), "2026-10-07");
        memory.remember("Plan na jutro: dentysta", List.of(), "2026-10-08");
        memory.remember("Notatka bez daty", List.of());

        assertThat(memory.forDate("2026-10-07"))
                .extracting(MemoryEntry::content)
                .containsExactly("Plan na dziś: zakupy");
        assertThat(memory.forDate("2026-10-08")).hasSize(1);
        assertThat(memory.forDate("2026-10-09")).isEmpty();
    }

    @Test
    void listsNotesFromDateOnwards() {
        FileLongTermMemory memory = new FileLongTermMemory(tempDir.resolve("m.json").toString());
        memory.remember("wczoraj", List.of(), "2026-10-06");
        memory.remember("dziś", List.of(), "2026-10-07");
        memory.remember("jutro", List.of(), "2026-10-08");

        assertThat(memory.fromDate("2026-10-07"))
                .extracting(MemoryEntry::content)
                .containsExactly("dziś", "jutro");
    }
}
