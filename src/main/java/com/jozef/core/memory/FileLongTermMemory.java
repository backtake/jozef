package com.jozef.core.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * File-backed long-term memory. The whole store is a JSON file (default
 * {@code data/memory.json}); it is loaded once at startup and written after
 * each mutation. Fine for a personal agent — not a database replacement.
 */
@Slf4j
public class FileLongTermMemory implements LongTermMemory {

    private final Path path;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Object lock = new Object();
    private final Map<String, MemoryEntry> entries = new LinkedHashMap<>();

    public FileLongTermMemory(String filePath) {
        String resolved = (filePath == null || filePath.isBlank())
                ? "data/memory.json"
                : filePath;
        this.path = Path.of(resolved);
        load();
    }

    private void load() {
        if (!Files.exists(path)) {
            return;
        }
        try {
            MemoryEntry[] array = mapper.readValue(path.toFile(), MemoryEntry[].class);
            synchronized (lock) {
                for (MemoryEntry entry : array) {
                    entries.put(entry.id(), entry);
                }
            }
            log.info("Loaded {} memory entries from {}", entries.size(), path);
        } catch (Exception e) {
            log.warn("Could not load memory from {}: {}", path, e.getMessage());
        }
    }

    @Override
    public MemoryEntry remember(String content, List<String> tags) {
        return remember(content, tags, null);
    }

    @Override
    public MemoryEntry remember(String content, List<String> tags, String date) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Memory content cannot be empty");
        }
        MemoryEntry entry = new MemoryEntry(
                UUID.randomUUID().toString(),
                content,
                tags == null ? List.of() : List.copyOf(tags),
                System.currentTimeMillis(),
                normalizeDate(date));
        synchronized (lock) {
            entries.put(entry.id(), entry);
            persist();
        }
        return entry;
    }

    @Override
    public List<MemoryEntry> recent(int limit) {
        synchronized (lock) {
            return entries.values().stream()
                    .sorted(Comparator.comparingLong(MemoryEntry::createdAtMillis).reversed())
                    .limit(Math.max(0, limit))
                    .toList();
        }
    }

    @Override
    public List<MemoryEntry> search(String query) {
        String q = query == null ? "" : query.toLowerCase();
        synchronized (lock) {
            return entries.values().stream()
                    .filter(e -> matches(e, q))
                    .sorted(Comparator.comparingLong(MemoryEntry::createdAtMillis).reversed())
                    .toList();
        }
    }

    @Override
    public List<MemoryEntry> forDate(String date) {
        String d = normalizeDate(date);
        if (d == null) {
            return List.of();
        }
        synchronized (lock) {
            return entries.values().stream()
                    .filter(e -> d.equals(e.date()))
                    .sorted(Comparator.comparingLong(MemoryEntry::createdAtMillis))
                    .toList();
        }
    }

    @Override
    public List<MemoryEntry> fromDate(String date) {
        String d = normalizeDate(date);
        synchronized (lock) {
            return entries.values().stream()
                    .filter(e -> d == null || (e.date() != null && e.date().compareTo(d) >= 0))
                    .sorted(Comparator.comparingLong(MemoryEntry::createdAtMillis))
                    .toList();
        }
    }

    private boolean matches(MemoryEntry entry, String q) {
        if (q.isBlank()) {
            return true;
        }
        if (entry.content().toLowerCase().contains(q)) {
            return true;
        }
        return entry.tags().stream().anyMatch(t -> t.toLowerCase().contains(q));
    }

    @Override
    public boolean forget(String id) {
        synchronized (lock) {
            boolean removed = entries.remove(id) != null;
            if (removed) {
                persist();
            }
            return removed;
        }
    }

    @Override
    public List<MemoryEntry> all() {
        synchronized (lock) {
            return List.copyOf(entries.values());
        }
    }

    private void persist() {
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), new ArrayList<>(entries.values()));
        } catch (IOException e) {
            log.error("Could not persist memory to {}: {}", path, e.getMessage(), e);
        }
    }

    /** Trims and validates an ISO date; returns null for blank/unknown values. */
    private static String normalizeDate(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        String d = date.trim();
        return d.matches("\\d{4}-\\d{2}-\\d{2}") ? d : null;
    }

    public Path path() {
        return path;
    }
}
