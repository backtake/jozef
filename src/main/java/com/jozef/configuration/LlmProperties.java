package com.jozef.configuration;

/**
 * LLM provider configuration.
 *
 * <pre>
 * jozef:
 *   llm:
 *     provider: ollama          # ollama | openai | deepseek | anthropic | gemini
 *     model: llama3.2
 *     endpoint: http://localhost:11434   # optional; provider-specific
 *     api-key-env: OPENAI_API_KEY        # env var holding the secret (never inline)
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
        FallbackProperties fallback
) {
    /** Optional secondary provider used automatically when the primary fails. */
    public record FallbackProperties(String provider, String model, String endpoint, String apiKeyEnv) {}
}
