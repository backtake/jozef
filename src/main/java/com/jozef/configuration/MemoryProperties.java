package com.jozef.configuration;

/**
 * Memory configuration.
 *
 * <pre>
 * jozef:
 *   memory:
 *     conversation-max-turns: 50   # turns kept as LLM history
 *     working-memory-size: 10      # long-term notes injected into context
 *     file: data/memory.json       # persistent long-term memory store
 * </pre>
 */
public record MemoryProperties(
        int conversationMaxTurns,
        int workingMemorySize,
        String file,
        VectorStoreProperties vectorStore
) {
    public record VectorStoreProperties(boolean enabled, String provider) {}
}
