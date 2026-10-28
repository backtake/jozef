package com.jozef.llm;

import lombok.extern.slf4j.Slf4j;

/**
 * Wraps a primary {@link LLMProvider} with an automatic fallback.
 *
 * When the primary provider fails (connection error, non-200 response, or an
 * exception), the same request is transparently retried against the fallback
 * provider. This lets Jozef prefer a local model (Ollama) while still
 * reaching an online model (e.g. DeepSeek) when the local one is unavailable
 * or cannot handle the request.
 */
@Slf4j
public class FallbackLlmProvider implements LLMProvider {

    private final LLMProvider primary;
    private final LLMProvider fallback;

    public FallbackLlmProvider(LLMProvider primary, LLMProvider fallback) {
        if (primary == null || fallback == null) {
            throw new IllegalArgumentException("Primary and fallback providers are required");
        }
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public String name() {
        return primary.name() + "→" + fallback.name();
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        try {
            ChatResponse response = primary.chat(request);
            if (!isError(response)) {
                return response;
            }
            log.warn("Primary LLM '{}' returned an error ({}). Falling back to '{}'.",
                    primary.name(), response == null ? "null" : response.content(), fallback.name());
        } catch (Exception e) {
            log.warn("Primary LLM '{}' threw an exception ({}). Falling back to '{}'.",
                    primary.name(), e.getMessage(), fallback.name());
        }
        return fallback.chat(request);
    }

    private boolean isError(ChatResponse response) {
        if (response == null) {
            return true;
        }
        if (response.hasToolCalls()) {
            return false;
        }
        String content = response.content();
        return content != null && content.startsWith("Error:");
    }
}
