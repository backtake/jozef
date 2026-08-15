package com.jozef.configuration;

public record MemoryProperties(
        int conversationMaxTurns,
        int workingMemorySize,
        VectorStoreProperties vectorStore
) {
    public record VectorStoreProperties(boolean enabled, String provider) {}
}
