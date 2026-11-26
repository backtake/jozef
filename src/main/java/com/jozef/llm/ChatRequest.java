package com.jozef.llm;

import java.util.List;

/**
 * Request sent to an LLM provider. Agnostic to which provider.
 *
 * {@code toolCallsAllowed=false} keeps the tool definitions (some APIs require them
 * whenever the history contains tool calls) but tells the model it must answer in
 * text now — used on the last step of a turn.
 */
public record ChatRequest(
        String systemPrompt,
        List<ChatMessage> messages,
        List<ToolSchema> tools,
        double temperature,
        int maxTokens,
        boolean toolCallsAllowed
) {
    public ChatRequest(String systemPrompt, List<ChatMessage> messages, List<ToolSchema> tools,
                       double temperature, int maxTokens) {
        this(systemPrompt, messages, tools, temperature, maxTokens, true);
    }

    public static ChatRequest simple(String systemPrompt, String userMessage, List<ToolSchema> tools) {
        return new ChatRequest(systemPrompt, List.of(ChatMessage.user(userMessage)), tools, 0.7, 2048);
    }
}
