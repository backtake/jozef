package com.jozef.core.memory;

import java.util.List;

/**
 * Long-term memory — facts/notes that persist across restarts.
 * The short-term conversational memory lives in {@code Session}.
 */
public interface LongTermMemory {

    /** Persists a new general note (not tied to a day) and returns the stored entry. */
    MemoryEntry remember(String content, List<String> tags);

    /**
     * Persists a new note and returns the stored entry.
     * The optional {@code date} (ISO {@code yyyy-MM-dd}) attaches the note to a
     * specific calendar day; {@code null} means a general, undated note.
     */
    MemoryEntry remember(String content, List<String> tags, String date);

    /** Most recently created notes, newest first. */
    List<MemoryEntry> recent(int limit);

    /** Notes whose content or tags match the query (case-insensitive substring). */
    List<MemoryEntry> search(String query);

    /** Notes attached to the given ISO date ({@code yyyy-MM-dd}), oldest first. */
    List<MemoryEntry> forDate(String date);

    /** Notes whose date is on or after the given ISO date, oldest first. */
    List<MemoryEntry> fromDate(String date);

    /** Removes a note by id. Returns true if something was removed. */
    boolean forget(String id);

    /** All stored notes. */
    List<MemoryEntry> all();
}
