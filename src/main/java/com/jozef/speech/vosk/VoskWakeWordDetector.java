package com.jozef.speech.vosk;

import com.jozef.speech.WakeWordDetector;
import com.jozef.speech.mic.MicrophoneAudioSource;
import lombok.extern.slf4j.Slf4j;
import org.vosk.Model;
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

    public VoskWakeWordDetector(String modelPath, String wakeWord) {
        this.modelPath = modelPath;
        this.wakeWord = (wakeWord == null || wakeWord.isBlank()) ? "hej jozef" : wakeWord;
    }

    @Override
    public String name() {
        return "vosk-wake-word";
    }

    @Override
    public String awaitWakeWord() {
        VoskNativeLibrary.prepare();
        try (Model model = new Model(modelPath);
             Recognizer recognizer = new Recognizer(model, SAMPLE_RATE);
             MicrophoneAudioSource mic = new MicrophoneAudioSource()) {

            mic.open();
            byte[] buffer = new byte[CHUNK_BYTES];
            boolean wakeDetected = false;
            long wakeDetectedAt = 0;

            while (true) {
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
        } catch (Exception e) {
            log.error("Vosk wake-word detection failed: {}", e.getMessage(), e);
            return "";
        }
    }

    // ─── Matching (package-private for tests) ───────────────

    static boolean containsWakeWord(String text, String wakeWord) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return normalize(text).contains(normalize(wakeWord));
    }

    /** Returns the text after the wake word (case-insensitive, best effort). */
    static String commandAfter(String text, String wakeWord) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String lower = text.toLowerCase(Locale.ROOT);
        String ww = wakeWord.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf(ww);
        if (idx < 0) {
            return "";
        }
        return text.substring(idx + ww.length()).trim();
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
