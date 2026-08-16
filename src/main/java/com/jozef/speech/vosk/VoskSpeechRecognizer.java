package com.jozef.speech.vosk;

import com.jozef.speech.SpeechRecognizer;
import com.jozef.speech.mic.MicrophoneAudioSource;
import lombok.extern.slf4j.Slf4j;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Offline speech-to-text via Vosk (local model, no cloud).
 *
 * Expects a downloaded + extracted Vosk model directory (e.g.
 * {@code data/vosk-model-small-pl-0.22}). If the model is missing, callers
 * should fall back to the console recognizer.
 */
@Slf4j
public class VoskSpeechRecognizer implements SpeechRecognizer {

    private static final float SAMPLE_RATE = 16_000.0f;
    private static final int CHUNK_BYTES = 4096;
    private static final long MAX_LISTEN_MILLIS = 15_000;

    private final String modelPath;

    public VoskSpeechRecognizer(String modelPath) {
        this.modelPath = modelPath;
    }

    /** True when a valid Vosk model directory exists at the given path. */
    public static boolean isModelAvailable(String modelPath) {
        if (modelPath == null || modelPath.isBlank()) {
            return false;
        }
        Path dir = Path.of(modelPath);
        // A proper Vosk model contains an acoustic-model directory "am".
        return Files.isDirectory(dir) && Files.isDirectory(dir.resolve("am"));
    }

    @Override
    public String name() {
        return "vosk";
    }

    @Override
    public String listen() {
        VoskNativeLibrary.prepare();
        try (Model model = new Model(modelPath);
             Recognizer recognizer = new Recognizer(model, SAMPLE_RATE);
             MicrophoneAudioSource mic = new MicrophoneAudioSource()) {

            mic.open();
            byte[] buffer = new byte[CHUNK_BYTES];
            long deadline = System.currentTimeMillis() + MAX_LISTEN_MILLIS;

            while (System.currentTimeMillis() < deadline) {
                int read = mic.read(buffer);
                if (read <= 0) {
                    continue;
                }
                if (recognizer.acceptWaveForm(buffer, read)) {
                    String text = VoskJson.text(recognizer.getResult());
                    if (!text.isBlank()) {
                        return text;
                    }
                }
            }

            String flushed = VoskJson.text(recognizer.getFinalResult());
            return flushed == null ? "" : flushed;
        } catch (Exception e) {
            log.error("Vosk STT failed: {}", e.getMessage(), e);
            return "";
        }
    }
}
