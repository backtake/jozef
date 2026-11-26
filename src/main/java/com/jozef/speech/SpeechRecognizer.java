package com.jozef.speech;

/**
 * Converts audio input to text. Replaceable.
 * Implementations: WhisperLocal, WhisperAPI, Deepgram, WindowsSpeech, AzureSpeech
 */
public interface SpeechRecognizer {
    String name();

    /**
     * Listen for speech and return transcribed text.
     * Blocks until speech is detected and transcribed.
     */
    String listen();

    /** Re-arm after {@link #interrupt()} — called by the voice loop right before listening. */
    default void clearInterrupt() {
    }

    /** Ask a blocking {@link #listen()} to return early (paused, Jozef starts speaking). Optional. */
    default void interrupt() {
    }
}
