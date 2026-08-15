package com.jozef.llm;

import java.util.List;

/**
 * Request sent to an LLM provider. Agnostic to which provider.
 */
public record ChatRequest(
        String systemPrompt,
        List<ChatMessage> messages,
        List<ToolSchema> tools,
        double temperature,
        int maxTokens
) {
    public static ChatRequest simple(String systemPrompt, String userMessage, List<ToolSchema> tools) {
        return new ChatRequest(
                systemPrompt,
                List.of(ChatMessage.user(userMessage)),
                tools,
                0.7,
                2048
        );
    }
}
