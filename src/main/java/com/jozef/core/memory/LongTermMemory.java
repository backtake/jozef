package com.jozef.core.memory;

import java.util.List;

/**
 * Long-term memory — facts/notes that persist across restarts.
 * The short-term conversational memory lives in {@code Session}.
 */
public interface LongTermMemory {

    /** Persists a new note and returns the stored entry. */
    MemoryEntry remember(String content, List<String> tags);

    /** Most recently created notes, newest first. */
    List<MemoryEntry> recent(int limit);

    /** Notes whose content or tags match the query (case-insensitive substring). */
    List<MemoryEntry> search(String query);

    /** Removes a note by id. Returns true if something was removed. */
    boolean forget(String id);

    /** All stored notes. */
    List<MemoryEntry> all();
}
