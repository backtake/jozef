package com.jozef.llm;

import java.util.List;

/**
 * Response from an LLM provider.
 */
public record ChatResponse(
        String content,
        List<ToolCall> toolCalls
) {
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
