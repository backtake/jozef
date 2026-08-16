package com.jozef.speech.vad;

/**
 * Simple energy-based voice activity detector.
 *
 * Computes the RMS of each PCM frame; frames louder than the threshold are
 * speech. An utterance "ends" when a speech run (longer than {@code minSpeechMillis})
 * is followed by silence of at least {@code minSilenceMillis}. A single loud
 * click is ignored thanks to the minimum-speech debounce.
 *
 * Time is tracked from the bytes fed in (audio time), not wall-clock time, so
 * the detector is deterministic and easy to test.
 */
public class EnergyVoiceActivityDetector implements VoiceActivityDetector {

    private static final int SAMPLE_RATE = 16_000;
    private static final int BYTES_PER_SECOND = SAMPLE_RATE * 2; // 16-bit mono

    private final double threshold;
    private final long minSpeechMillis;
    private final long minSilenceMillis;

    private long elapsedMillis = 0;
    private long speechStart = -1;
    private long lastSpeechAt = -1;
    private boolean ended = false;

    public EnergyVoiceActivityDetector(double threshold, long minSpeechMillis, long minSilenceMillis) {
        this.threshold = threshold;
        this.minSpeechMillis = minSpeechMillis;
        this.minSilenceMillis = minSilenceMillis;
    }

    /** Builds a detector tuned by sensitivity: high | medium | low. */
    public static EnergyVoiceActivityDetector forSensitivity(String sensitivity) {
        String s = sensitivity == null ? "medium" : sensitivity.toLowerCase();
        return switch (s) {
            case "high" -> new EnergyVoiceActivityDetector(250, 120, 600);
            case "low" -> new EnergyVoiceActivityDetector(1000, 120, 900);
            default -> new EnergyVoiceActivityDetector(500, 120, 800);
        };
    }

    @Override
    public void update(byte[] pcm, int length) {
        elapsedMillis += millisForBytes(length);
        boolean speech = rms(pcm, length) > threshold;

        if (speech) {
            lastSpeechAt = elapsedMillis;
            if (speechStart < 0) {
                speechStart = elapsedMillis;
            }
        } else if (speechStart >= 0 && (elapsedMillis - lastSpeechAt) >= minSilenceMillis) {
            if ((lastSpeechAt - speechStart) >= minSpeechMillis) {
                ended = true;
            }
            speechStart = -1;
        }
    }

    @Override
    public boolean utteranceEnded() {
        return ended;
    }

    @Override
    public void reset() {
        elapsedMillis = 0;
        speechStart = -1;
        lastSpeechAt = -1;
        ended = false;
    }

    /** Root-mean-square of a 16-bit little-endian PCM frame. */
    static double rms(byte[] pcm, int length) {
        if (pcm == null || length < 2) {
            return 0;
        }
        int samples = length / 2;
        double sum = 0;
        for (int i = 0; i + 1 < length; i += 2) {
            int sample = (pcm[i] & 0xFF) | (pcm[i + 1] << 8); // signed little-endian
            sum += (double) sample * sample;
        }
        return Math.sqrt(sum / samples);
    }

    /** Audio duration (millis) for the given number of 16-bit mono bytes. */
    static long millisForBytes(int length) {
        return (length * 1000L) / BYTES_PER_SECOND;
    }
}
