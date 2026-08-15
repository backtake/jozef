package com.jozef.configuration;

/**
 * Persona configuration — separates the assistant's identity from code.
 */
public record PersonaProperties(
        String name,
        String language,
        String style
) {}
