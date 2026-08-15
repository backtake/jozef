package com.jozef.core.context;

/**
 * Produces the system prompt that defines Jozef's persona and behavioral rules.
 * The persona lives in configuration, not in Java code.
 */
public interface SystemPromptProvider {

    String systemPrompt();
}
