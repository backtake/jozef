package com.jozef.speech.vad;

/**
 * Detects speech vs. silence in a PCM stream and signals the end of an utterance.
 */
public interface VoiceActivityDetector {

    /** Feed a PCM frame (16 kHz, 16-bit, mono, little-endian). */
    void update(byte[] pcm, int length);

    /** True when speech was detected and followed by enough silence. */
    boolean utteranceEnded();

    /** Reset detection state for a new utterance. */
    void reset();
}
