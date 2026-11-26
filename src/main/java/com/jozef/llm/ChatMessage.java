package com.jozef.llm;

import java.util.List;

/**
 * A single message in an LLM conversation.
 *
 * Supports plain user/assistant messages as well as the two special shapes
 * needed for tool calling:
 *   - assistant messages carrying {@code toolCalls} (the LLM's request to run tools),
 *     optionally with the provider's raw content to replay unchanged
 *   - tool result messages carrying {@code toolCallId}/{@code toolName} and the result
 */
public record ChatMessage(
        String role,
        String content,
        String toolName,
        List<ToolCall> toolCalls,
        String toolCallId,
        Object providerContent
) {
    public ChatMessage(String role, String content, String toolName, List<ToolCall> toolCalls) {
        this(role, content, toolName, toolCalls, null, null);
    }

    public ChatMessage(String role, String content, String toolName, List<ToolCall> toolCalls, String toolCallId) {
        this(role, content, toolName, toolCalls, toolCallId, null);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content, null, null, null, null);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content, null, null, null, null);
    }

    public static ChatMessage assistantToolCalls(List<ToolCall> toolCalls) {
        return assistantToolCalls("", toolCalls, null);
    }

    public static ChatMessage assistantToolCalls(String content, List<ToolCall> toolCalls) {
        return assistantToolCalls(content, toolCalls, null);
    }

    /** Assistant turn that requested tools; {@code providerContent} = raw blocks to replay as-is (may be null). */
    public static ChatMessage assistantToolCalls(String content, List<ToolCall> toolCalls, Object providerContent) {
        return new ChatMessage("assistant", content == null ? "" : content, null,
                toolCalls == null ? List.of() : List.copyOf(toolCalls), null, providerContent);
    }

    /** Result of a specific tool call — carries the call id providers require. */
    public static ChatMessage toolResult(ToolCall call, String content) {
        return new ChatMessage("tool", content, call.name(), null, call.id(), null);
    }
}
