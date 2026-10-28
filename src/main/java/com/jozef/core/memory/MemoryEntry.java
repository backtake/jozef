package com.jozef.core.memory;

import java.util.List;

/**
 * A single long-term memory entry (a fact/note Jozef is asked to remember).
 *
 * <p>The optional {@code date} field attaches the note to a specific calendar
 * day (ISO {@code yyyy-MM-dd}), turning the store into a simple notebook:
 * daily arrangements, plans for today, and plans for future days. A note with
 * {@code date == null} is a general fact not tied to any day.</p>
 */
public record MemoryEntry(
        String id,
        String content,
        List<String> tags,
        long createdAtMillis,
        String date
) {}
