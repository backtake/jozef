package com.jozef.llm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FallbackLlmProviderTest {

    private static LLMProvider provider(String name, String responseContent) {
        return new LLMProvider() {
            @Override public String name() { return name; }
            @Override public ChatResponse chat(ChatRequest request) {
                return new ChatResponse(responseContent, List.of());
            }
        };
    }

    @Test
    void usesPrimaryWhenItSucceeds() {
        LLMProvider primary = provider("primary", "ok");
        LLMProvider fallback = provider("fallback", "fallback answer");
        FallbackLlmProvider wrapped = new FallbackLlmProvider(primary, fallback);

        ChatResponse response = wrapped.chat(new ChatRequest(null, List.of(), List.of(), 0.7, 100));

        assertThat(response.content()).isEqualTo("ok");
    }

    @Test
    void fallsBackWhenPrimaryReturnsError() {
        LLMProvider primary = provider("primary", "Error: connection refused");
        LLMProvider fallback = provider("fallback", "fallback answer");
        FallbackLlmProvider wrapped = new FallbackLlmProvider(primary, fallback);

        ChatResponse response = wrapped.chat(new ChatRequest(null, List.of(), List.of(), 0.7, 100));

        assertThat(response.content()).isEqualTo("fallback answer");
    }

    @Test
    void fallsBackWhenPrimaryThrows() {
        LLMProvider primary = new LLMProvider() {
            @Override public String name() { return "primary"; }
            @Override public ChatResponse chat(ChatRequest request) {
                throw new IllegalStateException("boom");
            }
        };
        LLMProvider fallback = provider("fallback", "fallback answer");
        FallbackLlmProvider wrapped = new FallbackLlmProvider(primary, fallback);

        ChatResponse response = wrapped.chat(new ChatRequest(null, List.of(), List.of(), 0.7, 100));

        assertThat(response.content()).isEqualTo("fallback answer");
    }
}
