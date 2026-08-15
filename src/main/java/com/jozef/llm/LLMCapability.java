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
 * switching providers requires zero changes to agent logic.
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
        String providerName = props.provider() == null ? "ollama" : props.provider().toLowerCase();

        return switch (providerName) {
            case "ollama" -> new OllamaProvider(props.model(), props.endpoint());
            case "openai" -> {
                String apiKey = resolveApiKey(props.apiKeyEnv(), "OPENAI_API_KEY");
                yield new OpenAiLlmProvider(props.model(), apiKey, props.endpoint());
            }
            case "deepseek" -> {
                // DeepSeek exposes an OpenAI-compatible API (Bearer auth).
                String apiKey = resolveApiKey(props.apiKeyEnv(), "DEEPSEEK_API_KEY");
                String endpoint = (props.endpoint() == null || props.endpoint().isBlank())
                        ? "https://api.deepseek.com/v1/chat/completions"
                        : props.endpoint();
                String model = (props.model() == null || props.model().isBlank())
                        ? "deepseek-chat"
                        : props.model();
                yield new OpenAiLlmProvider(model, apiKey, endpoint);
            }
            case "anthropic", "gemini" ->
                    throw new IllegalArgumentException(
                            "LLM provider '" + providerName + "' is not implemented yet. "
                                    + "Supported: ollama, openai, deepseek.");
            default -> throw new IllegalArgumentException("Unknown LLM provider: " + providerName);
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
