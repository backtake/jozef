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

    /** Ask a blocking {@link #listen()} to return early (e.g. listening paused). Optional. */
    default void interrupt() {
    }
}
