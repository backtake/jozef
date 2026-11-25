package com.jozef.llm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FallbackLlmProviderTest {

    private static LLMProvider provider(String name, String responseContent) {
        return new LLMProvider() {
            @Override public String name() { return name; }
            @Override public ChatResponse chat(ChatRequest request) {
                return new ChatResponse(responseContent, List.of());
            }
        };
    }

    private static LLMProvider failing(String name) {
        return new LLMProvider() {
            @Override public String name() { return name; }
            @Override public ChatResponse chat(ChatRequest request) {
                throw new LlmException(name + " returned HTTP 503", 503, null);
            }
        };
    }

    private static ChatRequest request() {
        return new ChatRequest(null, List.of(), List.of(), 0.7, 100);
    }

    @Test
    void usesPrimaryWhenItSucceeds() {
        FallbackLlmProvider wrapped = new FallbackLlmProvider(provider("primary", "ok"), provider("fallback", "fb"));
        assertThat(wrapped.chat(request()).content()).isEqualTo("ok");
    }

    @Test
    void primaryAnswerThatMerelyMentionsAnErrorIsNotTreatedAsFailure() {
        // Content is the model's answer — only exceptions signal provider failure.
        FallbackLlmProvider wrapped = new FallbackLlmProvider(
                provider("primary", "Error: to słowo po angielsku znaczy błąd"), provider("fallback", "fb"));
        assertThat(wrapped.chat(request()).content()).startsWith("Error:");
    }

    @Test
    void fallsBackWhenPrimaryThrowsLlmException() {
        FallbackLlmProvider wrapped = new FallbackLlmProvider(failing("primary"), provider("fallback", "fallback answer"));
        assertThat(wrapped.chat(request()).content()).isEqualTo("fallback answer");
    }

    @Test
    void fallsBackWhenPrimaryThrowsAnything() {
        LLMProvider primary = new LLMProvider() {
            @Override public String name() { return "primary"; }
            @Override public ChatResponse chat(ChatRequest request) { throw new IllegalStateException("boom"); }
        };
        FallbackLlmProvider wrapped = new FallbackLlmProvider(primary, provider("fallback", "fallback answer"));
        assertThat(wrapped.chat(request()).content()).isEqualTo("fallback answer");
    }

    @Test
    void propagatesWhenBothFail() {
        FallbackLlmProvider wrapped = new FallbackLlmProvider(failing("primary"), failing("fallback"));
        assertThatThrownBy(() -> wrapped.chat(request())).isInstanceOf(LlmException.class);
    }
}
