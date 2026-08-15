package com.jozef.llm;

/**
 * The only thing the rest of the application knows about LLM.
 * Implementations: Ollama, OpenAI, Claude, Gemini, LMStudio, AnythingLLM.
 */
public interface LLMProvider {
    /** Unique provider name, e.g. "ollama", "openai" */
    String name();

    /** Send a chat request and get a response (possibly with tool calls). */
    ChatResponse chat(ChatRequest request);
}
