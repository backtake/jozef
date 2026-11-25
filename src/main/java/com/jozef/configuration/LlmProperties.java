package com.jozef.configuration;

import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * LLM provider configuration.
 *
 * <pre>
 * jozef:
 *   llm:
 *     provider: ollama          # ollama | openai | deepseek | anthropic
 *     model: llama3.2
 *     endpoint: http://localhost:11434   # optional; provider-specific
 *     api-key-env: OPENAI_API_KEY        # env var holding the secret (never inline)
 *     timeout-seconds: 90       # max time for one model call
 *     fallback:                 # optional; used when the primary provider fails
 *       provider: deepseek
 *       model: deepseek-chat
 *       endpoint: ""            # empty = provider default
 *       api-key-env: DEEPSEEK_API_KEY
 * </pre>
 */
public record LlmProperties(
        String provider,
        String model,
        String endpoint,
        String apiKeyEnv,
        double temperature,
        int maxTokens,
        FallbackProperties fallback,
        int timeoutSeconds
) {
    public static final int DEFAULT_TIMEOUT_SECONDS = 90;

    @ConstructorBinding
    public LlmProperties {
        if (timeoutSeconds <= 0) timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
    }

    public LlmProperties(String provider, String model, String endpoint, String apiKeyEnv,
                         double temperature, int maxTokens, FallbackProperties fallback) {
        this(provider, model, endpoint, apiKeyEnv, temperature, maxTokens, fallback, DEFAULT_TIMEOUT_SECONDS);
    }

    /** Optional secondary provider used automatically when the primary fails. */
    public record FallbackProperties(String provider, String model, String endpoint, String apiKeyEnv) {}
}
