package com.jozef.speech.vosk;

import com.jozef.speech.WakeWordDetector;
import com.jozef.speech.mic.MicrophoneAudioSource;
import lombok.extern.slf4j.Slf4j;

import org.vosk.Recognizer;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Wake-word detection via Vosk: continuously transcribes the microphone and
 * fires when the configured wake word is recognised. Returns the command spoken
 * after the wake word when it is part of the same utterance.
 */
@Slf4j
public class VoskWakeWordDetector implements WakeWordDetector {

    private static final float SAMPLE_RATE = 16_000.0f;
    private static final int CHUNK_BYTES = 4096;
    private static final long COMMAND_GRACE_MILLIS = 3_000;

    private final String modelPath;
    private final String wakeWord;
    private volatile boolean interrupted;

    public VoskWakeWordDetector(String modelPath, String wakeWord) {
        this.modelPath = modelPath;
        this.wakeWord = (wakeWord == null || wakeWord.isBlank()) ? "hej jozef" : wakeWord;
    }

    @Override
    public String name() {
        return "vosk-wake-word";
    }

    @Override
    public void clearInterrupt() {
        interrupted = false;
    }

    @Override
    public void interrupt() {
        interrupted = true;
    }

    @Override
    public String awaitWakeWord() {

        try (Recognizer recognizer = new Recognizer(VoskModels.get(modelPath), SAMPLE_RATE);
             MicrophoneAudioSource mic = new MicrophoneAudioSource()) {

            mic.open();
            byte[] buffer = new byte[CHUNK_BYTES];
            boolean wakeDetected = false;
            long wakeDetectedAt = 0;

            while (!interrupted) {
                int read = mic.read(buffer);
                if (read <= 0) {
                    continue;
                }

                if (recognizer.acceptWaveForm(buffer, read)) {
                    String text = VoskJson.text(recognizer.getResult());
                    if (wakeDetected) {
                        return commandAfter(text);
                    }
                    if (containsWakeWord(text)) {
                        wakeDetected = true;
                        wakeDetectedAt = System.currentTimeMillis();
                        String command = commandAfter(text);
                        if (!command.isBlank()) {
                            return command;
                        }
                    }
                } else {
                    String partial = VoskJson.partial(recognizer.getPartialResult());
                    if (!wakeDetected && containsWakeWord(partial)) {
                        wakeDetected = true;
                        wakeDetectedAt = System.currentTimeMillis();
                        String command = commandAfter(partial);
                        if (!command.isBlank()) {
                            return command;
                        }
                    }
                }

                if (wakeDetected && System.currentTimeMillis() - wakeDetectedAt > COMMAND_GRACE_MILLIS) {
                    return ""; // user said only the wake word
                }
            }
            return null; // interrupted (listening paused)
        } catch (Exception e) {
            log.error("Vosk wake-word detection failed: {}", e.getMessage(), e);
            return null; // NOT "" — that would mean "wake word heard", and the next speech would run as a command
        }
    }

    // ─── Matching (package-private for tests) ───────────────

    public static boolean containsWakeWord(String text, String wakeWord) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return normalize(text).contains(normalize(wakeWord));
    }

    /**
     * Returns the words spoken after the wake word, keeping their original
     * spelling. Matching is word-by-word and diacritics-insensitive, so the
     * recogniser's "hej józef otwórz pocztę" yields "otwórz pocztę" for the
     * configured "hej jozef" (a plain indexOf missed it and lost the command).
     */
    public static String commandAfter(String text, String wakeWord) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String[] words = text.trim().split("\\s+");
        String[] wake = normalize(wakeWord).split(" ");
        for (int i = 0; i + wake.length <= words.length; i++) {
            boolean match = true;
            for (int j = 0; j < wake.length; j++) {
                String w = normalize(words[i + j]).replaceAll("[^a-z0-9]", "");
                // "józefie" (vocative) still counts as the name.
                if (!(w.equals(wake[j]) || (j == wake.length - 1 && w.startsWith(wake[j])))) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return String.join(" ", java.util.Arrays.copyOfRange(words, i + wake.length, words.length))
                        .replaceFirst("^[,.!?\\s]+", "").trim();
            }
        }
        return "";
    }

    /** Lowercase + strip diacritics + collapse whitespace. */
    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String lower = text.toLowerCase(Locale.ROOT);
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "").replaceAll("\\s+", " ").trim();
    }

    private boolean containsWakeWord(String text) {
        return containsWakeWord(text, wakeWord);
    }

    private String commandAfter(String text) {
        return commandAfter(text, wakeWord);
    }
}
