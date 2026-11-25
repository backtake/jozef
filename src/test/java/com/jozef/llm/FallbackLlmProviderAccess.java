package com.jozef.llm;

/** Test access to the package-private constructor. */
public final class FallbackLlmProviderAccess {
    private FallbackLlmProviderAccess() {}

    public static LLMProvider create(LLMProvider primary, LLMProvider fallback) {
        return new FallbackLlmProvider(primary, fallback);
    }
}
