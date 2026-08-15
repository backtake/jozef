package com.jozef.llm;

import java.util.List;

/**
 * A single message in an LLM conversation.
 *
 * Supports plain user/assistant messages as well as the two special shapes
 * needed for tool calling:
 *   - assistant messages carrying {@code toolCalls} (the LLM's request to run tools)
 *   - tool result messages carrying {@code toolName} and the execution result
 */
public record ChatMessage(
        String role,
        String content,
        String toolName,
        List<ToolCall> toolCalls
) {
    public static ChatMessage user(String content) {
        return new ChatMessage("user", content, null, null);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content, null, null);
    }

    public static ChatMessage assistantToolCalls(List<ToolCall> toolCalls) {
        return new ChatMessage("assistant", "", null, toolCalls == null ? List.of() : toolCalls);
    }

    public static ChatMessage tool(String content) {
        return new ChatMessage("tool", content, null, null);
    }

    public static ChatMessage tool(String toolName, String content) {
        return new ChatMessage("tool", content, toolName, null);
    }
}
