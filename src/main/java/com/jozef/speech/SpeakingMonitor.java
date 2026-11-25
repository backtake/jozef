package com.jozef.speech;

/**
 * Serialises Jozef's own speech and remembers when it last spoke.
 *
 * <ul>
 *   <li>Only one utterance plays at a time (voice loop, command window and proactive
 *       notices never talk over each other).</li>
 *   <li>The voice loop discards anything recognised while Jozef was speaking — otherwise
 *       a notice such as "Temat: tak, potwierdzam" read aloud could be heard by the
 *       microphone and taken as the user's answer.</li>
 * </ul>
 */
public final class SpeakingMonitor {

    private final Object playLock = new Object();
    private volatile long speakingSince = -1;
    private volatile long lastSpokeUntil = 0;

    /** Wraps a synthesizer so every utterance goes through this monitor. */
    public SpeechSynthesizer guard(SpeechSynthesizer delegate) {
        return new SpeechSynthesizer() {
            @Override public String name() { return delegate.name(); }
            @Override public void speak(String text) {
                if (text == null || text.isBlank()) return;
                synchronized (playLock) {
                    speakingSince = System.currentTimeMillis();
                    try {
                        delegate.speak(text);
                    } finally {
                        lastSpokeUntil = System.currentTimeMillis();
                        speakingSince = -1;
                    }
                }
            }
        };
    }

    /**
     * True if Jozef was speaking at some moment while the microphone listened in
     * [from, to] — i.e. playback is still running, or it ended after listening began.
     */
    public boolean spokeDuring(long fromMillis, long toMillis) {
        long since = speakingSince;
        if (since >= 0 && since <= toMillis) return true;
        return lastSpokeUntil > fromMillis;
    }

    public boolean isSpeaking() {
        return speakingSince >= 0;
    }
}
