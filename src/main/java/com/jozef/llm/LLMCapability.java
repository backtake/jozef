package com.jozef.llm;

import com.jozef.configuration.LlmProperties;
import com.jozef.core.capabilities.Capability;
import com.jozef.llm.ollama.OllamaProvider;
import com.jozef.llm.openai.OpenAiLlmProvider;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.function.Function;

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
    private final Function<String, String> env;
    private final String resolvedProvider;
    private LLMProvider provider;

    public LLMCapability(LlmProperties props) {
        this(props, System::getenv);
    }

    LLMCapability(LlmProperties props, Function<String, String> env) {
        this.props = props;
        this.env = env;
        this.resolvedProvider = resolveProviderName(props.provider());
        this.provider = createProvider(props);
    }

    /**
     * {@code auto}: the best model the user has access to — OpenAI when
     * OPENAI_API_KEY is set, else DeepSeek with DEEPSEEK_API_KEY, else local Ollama.
     * Small local models handle dozens of tools and Polish poorly, so a cloud
     * model is preferred whenever a key exists.
     */
    private String resolveProviderName(String configured) {
        String name = configured == null || configured.isBlank() ? "auto" : configured.toLowerCase();
        if (!"auto".equals(name)) {
            return name;
        }
        if (!isBlank(env.apply("OPENAI_API_KEY"))) return "openai";
        if (!isBlank(env.apply("DEEPSEEK_API_KEY"))) return "deepseek";
        return "ollama";
    }

    /** The concrete primary provider in use ("openai", "deepseek", "ollama"…). */
    public String resolvedProvider() {
        return resolvedProvider;
    }

    private LLMProvider createProvider(LlmProperties props) {
        boolean auto = props.provider() == null || props.provider().isBlank() || "auto".equalsIgnoreCase(props.provider());
        // In auto mode the configured model/endpoint belong to whichever provider the user
        // wrote them for; only use them when they clearly fit the resolved provider.
        String model = auto && !modelFits(resolvedProvider, props.model()) ? null : props.model();
        String endpoint = auto && !"ollama".equals(resolvedProvider) ? null : props.endpoint();
        LLMProvider primary = buildProvider(resolvedProvider, model, endpoint, auto ? null : props.apiKeyEnv());

        LlmProperties.FallbackProperties fb = props.fallback();
        if (fb != null && !isBlank(fb.provider()) && !fb.provider().equalsIgnoreCase(resolvedProvider)) {
            LLMProvider fallback = buildProvider(
                    fb.provider(), fb.model(), fb.endpoint(), fb.apiKeyEnv());
            log.info("LLM fallback enabled: {} → {}", primary.name(), fallback.name());
            return new FallbackLlmProvider(primary, fallback);
        }
        return primary;
    }

    private static boolean modelFits(String provider, String model) {
        if (isBlank(model)) return false;
        String m = model.toLowerCase();
        return switch (provider) {
            case "openai" -> m.startsWith("gpt") || m.matches("o\\d.*") || m.startsWith("chatgpt");
            case "deepseek" -> m.startsWith("deepseek");
            default -> !m.startsWith("gpt") && !m.startsWith("deepseek");
        };
    }

    private LLMProvider buildProvider(String providerName, String model, String endpoint, String apiKeyEnv) {
        String name = providerName == null || providerName.isBlank() ? "ollama" : providerName.toLowerCase();
        Duration timeout = Duration.ofSeconds(props.timeoutSeconds() > 0 ? props.timeoutSeconds() : LlmProperties.DEFAULT_TIMEOUT_SECONDS);

        return switch (name) {
            case "ollama" -> new OllamaProvider(
                    defaultIfBlank(model, "llama3.2"),
                    defaultIfBlank(endpoint, "http://localhost:11434"),
                    timeout);
            case "openai" -> new OpenAiLlmProvider("openai",
                    defaultIfBlank(model, "gpt-4.1-mini"),
                    resolveApiKey(apiKeyEnv, "OPENAI_API_KEY"),
                    defaultIfBlank(endpoint, OpenAiLlmProvider.DEFAULT_ENDPOINT),
                    timeout);
            // DeepSeek exposes an OpenAI-compatible API (Bearer auth).
            case "deepseek" -> new OpenAiLlmProvider("deepseek",
                    defaultIfBlank(model, "deepseek-chat"),
                    resolveApiKey(apiKeyEnv, "DEEPSEEK_API_KEY"),
                    defaultIfBlank(endpoint, "https://api.deepseek.com/v1/chat/completions"),
                    timeout);
            case "gemini" -> throw new IllegalArgumentException(
                    "LLM provider gemini is not implemented yet. Supported: ollama, openai, deepseek.");
            default -> throw new IllegalArgumentException("Unknown LLM provider: " + name);
        };
    }

    private String resolveApiKey(String envVar, String fallbackEnvVar) {
        if (envVar != null && !envVar.isBlank()) {
            String value = env.apply(envVar);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return env.apply(fallbackEnvVar);
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
        log.info("LLM capability initialized: provider={} (configured: {}), using {}", resolvedProvider, props.provider(), provider == null ? "-" : provider.name());
    }

    @Override
    public void shutdown() {
        log.info("LLM capability shut down");
        provider = null;
    }
}
