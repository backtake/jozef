package com.jozef.configuration;

/**
 * LLM provider configuration.
 *
 * <pre>
 * jozef:
 *   llm:
 *     provider: ollama          # ollama | openai | anthropic | gemini
 *     model: llama3.2
 *     endpoint: http://localhost:11434   # optional; provider-specific
 *     api-key-env: OPENAI_API_KEY        # env var holding the secret (never inline)
 * </pre>
 */
public record LlmProperties(
        String provider,
        String model,
        String endpoint,
        String apiKeyEnv,
        double temperature,
        int maxTokens
) {}
