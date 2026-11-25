package com.jozef.llm;

/**
 * The only thing the rest of the application knows about LLM.
 * Implementations: Ollama, OpenAI-compatible (OpenAI, DeepSeek, LM Studio…), Anthropic.
 */
public interface LLMProvider {
    /** Unique provider name, e.g. "ollama", "openai" */
    String name();

    /**
     * Send a chat request and get a response (possibly with tool calls).
     *
     * @throws LlmException when no answer could be obtained — never return error text as content
     */
    ChatResponse chat(ChatRequest request);
}
