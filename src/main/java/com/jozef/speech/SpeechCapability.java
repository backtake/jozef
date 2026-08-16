package com.jozef.speech;

import com.jozef.configuration.SpeechProperties;
import com.jozef.configuration.WakeWordProperties;
import com.jozef.core.capabilities.Capability;
import com.jozef.speech.console.ConsoleInputRecognizer;
import com.jozef.speech.console.ConsoleSpeechSynthesizer;
import com.jozef.speech.tts.EdgeTtsSynthesizer;
import com.jozef.speech.tts.WindowsSpeechSynthesizer;
import com.jozef.speech.vosk.VoskModelManager;
import com.jozef.speech.vosk.VoskSpeechRecognizer;
import com.jozef.speech.vosk.VoskWakeWordDetector;
import lombok.extern.slf4j.Slf4j;

/**
 * Provides {@link SpeechRecognizer} (STT) and {@link SpeechSynthesizer} (TTS)
 * to the system, chosen from configuration, plus an optional
 * {@link WakeWordDetector}.
 *
 * STT providers: {@code console} (dev fallback) or {@code vosk} (offline local
 * model). When Vosk's model is missing, we fall back to console and log
 * download instructions.
 */
@Slf4j
public class SpeechCapability implements Capability {

    private static final String DEFAULT_VOSK_MODEL = "data/vosk-model-small-pl-0.22";

    private final SpeechRecognizer recognizer;
    private final SpeechSynthesizer synthesizer;
    private final WakeWordDetector wakeWordDetector;

    public SpeechCapability(SpeechProperties props, WakeWordProperties wakeWord) {
        this.recognizer = createRecognizer(props);
        this.synthesizer = createSynthesizer(props);
        this.wakeWordDetector = createWakeWordDetector(props, wakeWord);
    }

    private SpeechRecognizer createRecognizer(SpeechProperties props) {
        String provider = props != null && props.stt() != null && props.stt().provider() != null
                ? props.stt().provider().toLowerCase()
                : "console";

        if ("vosk".equals(provider)) {
            String modelPath = resolveModelPath(props.stt().model());
            if (VoskSpeechRecognizer.isModelAvailable(modelPath)) {
                log.info("Using Vosk STT with model {}", modelPath);
                return new VoskSpeechRecognizer(modelPath);
            }
            if (props.stt().autoDownload()) {
                log.info("Model Vosk nie znaleziony — próbuję pobrać i zainstalować automatycznie.");
                if (VoskModelManager.install(modelPath)) {
                    return new VoskSpeechRecognizer(modelPath);
                }
                log.warn("Automatyczna instalacja modelu Vosk nie powiodła się — fallback do konsoli.");
            } else {
                log.warn("Vosk model not found at '{}' — falling back to console input.", modelPath);
                log.warn("Pobierz model z https://alphacephei.com/vosk/models "
                        + "(np. vosk-model-small-pl-0.22) i rozpakuj do katalogu data/, "
                        + "albo ustaw jozef.speech.stt.auto-download=true.");
            }
        }
        return new ConsoleInputRecognizer();
    }

    private WakeWordDetector createWakeWordDetector(SpeechProperties props, WakeWordProperties wakeWord) {
        if (wakeWord == null || !wakeWord.enabled()) {
            return null;
        }
        if (!"vosk".equals(recognizer.name())) {
            log.warn("Wake word requires vosk STT (current STT: {}) — wake word disabled.",
                    recognizer.name());
            return null;
        }
        String modelPath = resolveModelPath(props != null && props.stt() != null ? props.stt().model() : null);
        log.info("Wake word enabled: \"{}\"", wakeWord.word());
        return new VoskWakeWordDetector(modelPath, wakeWord.word());
    }

    private String resolveModelPath(String configured) {
        if (configured == null || configured.isBlank()) {
            return DEFAULT_VOSK_MODEL;
        }
        // Bare model name (no directory) → keep it under data/.
        if (!configured.contains("/") && !configured.contains("\\")) {
            return "data/" + configured;
        }
        return configured;
    }

    private SpeechSynthesizer createSynthesizer(SpeechProperties props) {
        String provider = (props != null && props.tts() != null && props.tts().provider() != null)
                ? props.tts().provider().toLowerCase()
                : "edge";
        String voice = (props != null && props.tts() != null) ? props.tts().voice() : null;
        return switch (provider) {
            case "edge" -> new EdgeTtsSynthesizer(voice);
            case "windows" -> new WindowsSpeechSynthesizer();
            default -> new ConsoleSpeechSynthesizer();
        };
    }

    public SpeechRecognizer recognizer() {
        return recognizer;
    }

    public SpeechSynthesizer synthesizer() {
        return synthesizer;
    }

    /** May be null when the wake word is disabled or unsupported. */
    public WakeWordDetector wakeWordDetector() {
        return wakeWordDetector;
    }

    @Override
    public String name() {
        return "speech";
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public void initialize() {
        log.info("Speech capability initialized: stt={}, tts={}, wake-word={}",
                recognizer.name(), synthesizer.name(),
                wakeWordDetector != null ? wakeWordDetector.name() : "off");
    }

    @Override
    public void shutdown() {
        log.info("Speech capability shut down");
    }
}
