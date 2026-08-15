package com.jozef.speech;

/**
 * Converts text to spoken audio. Replaceable.
 * Implementations: ElevenLabs, WindowsSpeech, AzureSpeech, OpenAITTS
 */
public interface SpeechSynthesizer {
    String name();

    /**
     * Speak the given text. Can be async (returns immediately).
     */
    void speak(String text);
}
