package com.jozef.llm;

import java.util.List;

/**
 * Response from an LLM provider.
 *
 * {@code providerContent} is the provider's raw assistant content (e.g. Anthropic
 * content blocks including {@code thinking}), kept opaque so it can be sent back
 * unchanged on the next tool-calling step — some APIs require exactly that.
 */
public record ChatResponse(
        String content,
        List<ToolCall> toolCalls,
        Object providerContent
) {
    public ChatResponse(String content, List<ToolCall> toolCalls) {
        this(content, toolCalls, null);
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
