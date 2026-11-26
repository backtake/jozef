package com.jozef.speech;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Serialises Jozef's own speech and remembers when it last spoke.
 *
 * <ul>
 *   <li>Only one utterance plays at a time (voice loop, command window and proactive
 *       notices never talk over each other).</li>
 *   <li>Listeners hear when playback starts, so the voice loop can stop listening
 *       and start afresh afterwards — the microphone never transcribes Jozef's own
 *       voice into a command, and the user's next command is not mixed with it.</li>
 * </ul>
 */
public final class SpeakingMonitor {

    private final Object playLock = new Object();
    private final List<Runnable> onSpeechStart = new CopyOnWriteArrayList<>();
    private volatile long speakingSince = -1;
    private volatile long lastSpokeUntil = 0;

    /** Called (on the speaking thread) right before Jozef starts talking. */
    public void addSpeechStartListener(Runnable listener) {
        onSpeechStart.add(listener);
    }

    /** Wraps a synthesizer so every utterance goes through this monitor. */
    public SpeechSynthesizer guard(SpeechSynthesizer delegate) {
        return new SpeechSynthesizer() {
            @Override public String name() { return delegate.name(); }
            @Override public void speak(String text) {
                if (text == null || text.isBlank()) return;
                synchronized (playLock) {
                    speakingSince = System.currentTimeMillis();
                    for (Runnable listener : onSpeechStart) {
                        try {
                            listener.run();
                        } catch (RuntimeException ignored) {
                            // a listener must never stop Jozef from speaking
                        }
                    }
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

    /** When the last utterance finished (epoch millis), 0 if never. */
    public long lastSpokeUntil() {
        return lastSpokeUntil;
    }

    /** Blocks while Jozef is speaking (bounded). */
    public void awaitSilence(long maxMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + maxMillis;
        while (isSpeaking() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
    }
}
