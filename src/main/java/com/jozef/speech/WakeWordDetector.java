package com.jozef.speech;

/**
 * Detects the configured wake word (e.g. "hej Jozef") in continuous audio.
 */
public interface WakeWordDetector {

    String name();

    /**
     * Blocks until the wake word is detected.
     *
     * @return the command spoken after the wake word, or an empty string when
     *         the user said only the wake word (the caller should then listen
     *         for the command separately).
     */
    String awaitWakeWord();

    /** Re-arm after {@link #interrupt()} — called by the voice loop right before listening. */
    default void clearInterrupt() {
    }

    /** Ask a blocking {@link #awaitWakeWord()} to return {@code null} early (paused, Jozef speaking). Optional. */
    default void interrupt() {
    }
}
