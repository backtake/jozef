package com.jozef.core.memory;

import java.util.List;

/**
 * A single long-term memory entry (a fact/note Jozef is asked to remember).
 */
public record MemoryEntry(
        String id,
        String content,
        List<String> tags,
        long createdAtMillis
) {}
