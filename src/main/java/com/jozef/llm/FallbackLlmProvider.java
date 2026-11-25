package com.jozef.llm;

import lombok.extern.slf4j.Slf4j;

/**
 * Wraps a primary {@link LLMProvider} with an automatic fallback.
 *
 * When the primary provider fails (throws — connection error, timeout, non-2xx
 * response), the same request is transparently retried against the fallback
 * provider. This lets Jozef prefer one model while still answering when it is
 * unavailable.
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
            if (response != null) {
                return response;
            }
            log.warn("Primary LLM '{}' returned no response. Falling back to '{}'.", primary.name(), fallback.name());
        } catch (Exception e) {
            log.warn("Primary LLM '{}' failed ({}). Falling back to '{}'.",
                    primary.name(), e.getMessage(), fallback.name());
        }
        return fallback.chat(request);
    }
}
