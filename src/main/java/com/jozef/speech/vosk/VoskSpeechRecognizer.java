package com.jozef.speech.vosk;

import com.jozef.configuration.SpeechProperties.VadProperties;
import com.jozef.speech.SpeechRecognizer;
import com.jozef.speech.mic.MicrophoneAudioSource;
import com.jozef.speech.vad.EnergyVoiceActivityDetector;
import com.jozef.speech.vad.VoiceActivityDetector;
import lombok.extern.slf4j.Slf4j;

import org.vosk.Recognizer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Offline speech-to-text via Vosk (local model, no cloud).
 *
 * With VAD enabled, final results are accumulated and the utterance is returned
 * only after speech is followed by silence — so multi-phrase commands are not
 * cut off. Without VAD, the first final result is returned immediately.
 */
@Slf4j
public class VoskSpeechRecognizer implements SpeechRecognizer {

    private static final float SAMPLE_RATE = 16_000.0f;
    private static final int CHUNK_BYTES = 4096;
    private static final long MAX_LISTEN_MILLIS = 15_000;

    private final String modelPath;
    private final VadProperties vad;
    private volatile boolean interrupted;

    public VoskSpeechRecognizer(String modelPath, VadProperties vad) {
        this.modelPath = modelPath;
        this.vad = vad;
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
    public void clearInterrupt() {
        interrupted = false;
    }

    @Override
    public void interrupt() {
        interrupted = true;
    }

    @Override
    public String name() {
        return "vosk";
    }

    @Override
    public String listen() {

        try (Recognizer recognizer = new Recognizer(VoskModels.get(modelPath), SAMPLE_RATE);
             MicrophoneAudioSource mic = new MicrophoneAudioSource()) {

            mic.open();
            byte[] buffer = new byte[CHUNK_BYTES];
            VoiceActivityDetector vad = createVad();
            List<String> finals = new ArrayList<>();
            long deadline = System.currentTimeMillis() + MAX_LISTEN_MILLIS;

            while (System.currentTimeMillis() < deadline && !interrupted) {
                int read = mic.read(buffer);
                if (read <= 0) {
                    continue;
                }
                if (vad != null) {
                    vad.update(buffer, read);
                }
                if (recognizer.acceptWaveForm(buffer, read)) {
                    String text = VoskJson.text(recognizer.getResult());
                    if (!text.isBlank()) {
                        if (vad == null) {
                            return text; // VAD off → first final wins
                        }
                        finals.add(text);
                    }
                }
                if (vad != null && vad.utteranceEnded()) {
                    break;
                }
            }

            String flushed = VoskJson.text(recognizer.getFinalResult());
            if (!flushed.isBlank() && !finals.contains(flushed)) {
                finals.add(flushed);
            }
            return String.join(" ", finals).trim();
        } catch (Exception e) {
            log.error("Vosk STT failed: {}", e.getMessage(), e);
            return "";
        }
    }

    private VoiceActivityDetector createVad() {
        if (vad == null || !vad.enabled()) {
            return null;
        }
        return EnergyVoiceActivityDetector.forSensitivity(vad.sensitivity());
    }
}
