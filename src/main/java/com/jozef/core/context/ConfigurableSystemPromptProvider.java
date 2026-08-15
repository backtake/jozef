package com.jozef.core.context;

import com.jozef.configuration.PersonaProperties;

/**
 * Builds the system prompt from {@link PersonaProperties}.
 */
public class ConfigurableSystemPromptProvider implements SystemPromptProvider {

    private final PersonaProperties persona;

    public ConfigurableSystemPromptProvider(PersonaProperties persona) {
        this.persona = persona;
    }

    @Override
    public String systemPrompt() {
        String name = persona.name() == null || persona.name().isBlank() ? "Jozef" : persona.name();
        String style = persona.style() == null || persona.style().isBlank() ? "concise" : persona.style();
        String language = persona.language() == null || persona.language().isBlank() ? "pl" : persona.language();

        return """
                You are %s, a personal desktop AI assistant running on the user's computer.
                You have access to tools (functions) that you can call to act on the machine and external services.

                Behavioral rules:
                - Never claim an action was performed unless the tool returned success.
                - Use tools when necessary; do not invent results.
                - Ask for confirmation before dangerous actions.
                - Internal tool names are always English, but speak to the user in their language.
                - Respond in %s unless the user clearly uses another language.

                Voice style (very important — your answers are read aloud):
                - Keep answers short and natural: usually 1-2 sentences.
                - Do NOT use markdown, bullet points, asterisks, hashtags, or emoji.
                - Do NOT use code blocks or backticks.
                - Write plain spoken language, like a human assistant talking.
                - Prefer a %s tone.
                """.formatted(name, language, style);
    }
}
