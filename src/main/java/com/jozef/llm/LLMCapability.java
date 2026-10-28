package com.jozef.llm;

import com.jozef.configuration.LlmProperties;
import com.jozef.core.capabilities.Capability;
import com.jozef.llm.ollama.OllamaProvider;
import com.jozef.llm.openai.OpenAiLlmProvider;
import lombok.extern.slf4j.Slf4j;

/**
 * Provides the LLMProvider implementation based on configuration.
 *
 * The active provider is chosen entirely from {@code jozef.llm.provider};
 * switching providers requires zero changes to agent logic. An optional
 * {@code jozef.llm.fallback} section wraps the primary provider with an
 * automatic fallback (e.g. local Ollama first, DeepSeek when it fails).
 */
@Slf4j
public class LLMCapability implements Capability {

    private final LlmProperties props;
    private LLMProvider provider;

    public LLMCapability(LlmProperties props) {
        this.props = props;
        this.provider = createProvider(props);
    }

    private LLMProvider createProvider(LlmProperties props) {
        LLMProvider primary = buildProvider(
                props.provider(), props.model(), props.endpoint(), props.apiKeyEnv());

        LlmProperties.FallbackProperties fb = props.fallback();
        if (fb != null && !isBlank(fb.provider())
                && !fb.provider().equalsIgnoreCase(props.provider() == null ? "" : props.provider())) {
            LLMProvider fallback = buildProvider(
                    fb.provider(), fb.model(), fb.endpoint(), fb.apiKeyEnv());
            log.info("LLM fallback enabled: {} → {}", primary.name(), fallback.name());
            return new FallbackLlmProvider(primary, fallback);
        }
        return primary;
    }

    private LLMProvider buildProvider(String providerName, String model, String endpoint, String apiKeyEnv) {
        String name = providerName == null || providerName.isBlank() ? "ollama" : providerName.toLowerCase();

        return switch (name) {
            case "ollama" -> new OllamaProvider(
                    defaultIfBlank(model, "llama3.2"),
                    defaultIfBlank(endpoint, "http://localhost:11434"));
            case "openai" -> {
                String apiKey = resolveApiKey(apiKeyEnv, "OPENAI_API_KEY");
                yield new OpenAiLlmProvider(
                        defaultIfBlank(model, "gpt-4o-mini"),
                        apiKey,
                        defaultIfBlank(endpoint, "https://api.openai.com/v1/chat/completions"));
            }
            case "deepseek" -> {
                // DeepSeek exposes an OpenAI-compatible API (Bearer auth).
                String apiKey = resolveApiKey(apiKeyEnv, "DEEPSEEK_API_KEY");
                yield new OpenAiLlmProvider(
                        defaultIfBlank(model, "deepseek-chat"),
                        apiKey,
                        defaultIfBlank(endpoint, "https://api.deepseek.com/v1/chat/completions"));
            }
            case "anthropic", "gemini" ->
                    throw new IllegalArgumentException(
                            "LLM provider '" + name + "' is not implemented yet. "
                                    + "Supported: ollama, openai, deepseek.");
            default -> throw new IllegalArgumentException("Unknown LLM provider: " + name);
        };
    }

    private String resolveApiKey(String envVar, String fallbackEnvVar) {
        if (envVar != null && !envVar.isBlank()) {
            String value = System.getenv(envVar);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return System.getenv(fallbackEnvVar);
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public LLMProvider provider() {
        return provider;
    }

    @Override
    public String name() {
        return "llm";
    }

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public void initialize() {
        log.info("LLM capability initialized: provider={}, model={}", props.provider(), props.model());
    }

    @Override
    public void shutdown() {
        log.info("LLM capability shut down");
        provider = null;
    }
}
