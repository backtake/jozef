package com.jozef.speech;

import com.jozef.configuration.SpeechProperties;
import com.jozef.configuration.WakeWordProperties;
import com.jozef.core.capabilities.Capability;
import com.jozef.speech.console.ConsoleInputRecognizer;
import com.jozef.speech.console.ConsoleSpeechSynthesizer;
import com.jozef.speech.tts.FallbackSpeechSynthesizer;
import com.jozef.speech.tts.OpenAiSpeechSynthesizer;
import com.jozef.speech.tts.WindowsSpeechSynthesizer;
import com.jozef.speech.vosk.VoskModelManager;
import com.jozef.speech.vosk.VoskSpeechRecognizer;
import com.jozef.speech.vosk.VoskWakeWordDetector;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

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

    private final Function<String, String> env;
    private final SpeakingMonitor speakingMonitor = new SpeakingMonitor();
    private final String llmProvider;

    public SpeechCapability(SpeechProperties props, WakeWordProperties wakeWord) {
        this(props, wakeWord, null, System::getenv);
    }

    /**
     * @param llmProvider the language-model provider in use; "auto" TTS only sends
     *                    speech to OpenAI when the conversation already goes there
     */
    public SpeechCapability(SpeechProperties props, WakeWordProperties wakeWord, String llmProvider) {
        this(props, wakeWord, llmProvider, System::getenv);
    }

    SpeechCapability(SpeechProperties props, WakeWordProperties wakeWord, String llmProvider,
                     Function<String, String> env) {
        this.env = env;
        this.llmProvider = llmProvider;
        this.recognizer = createRecognizer(props);
        this.synthesizer = speakingMonitor.guard(createSynthesizer(props));
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
                return new VoskSpeechRecognizer(modelPath, props.vad());
            }
            if (props.stt().autoDownload()) {
                log.info("Model Vosk nie znaleziony — próbuję pobrać i zainstalować automatycznie.");
                if (VoskModelManager.install(modelPath)) {
                    return new VoskSpeechRecognizer(modelPath, props.vad());
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
                : "auto";
        String voice = (props != null && props.tts() != null) ? props.tts().voice() : null;
        String openAiKey = env.apply("OPENAI_API_KEY");
        boolean hasOpenAi = openAiKey != null && !openAiKey.isBlank();

        // Every chain ends with offline Windows TTS (and the console as the last resort),
        // so a network/billing problem never leaves Jozef mute.
        List<SpeechSynthesizer> chain = new ArrayList<>();
        switch (provider) {
            case "console" -> {
                return new ConsoleSpeechSynthesizer();
            }
            case "windows" -> chain.add(new WindowsSpeechSynthesizer());
            case "openai" -> {
                if (hasOpenAi) {
                    chain.add(new OpenAiSpeechSynthesizer(openAiKey, voice));
                } else {
                    log.warn("TTS 'openai' needs OPENAI_API_KEY — using Windows voice.");
                }
                chain.add(new WindowsSpeechSynthesizer());
            }
            default -> {
                if ("edge".equals(provider)) {
                    log.warn("TTS 'edge' is no longer available (Microsoft locked the unofficial endpoint) — using 'auto'.");
                } else if (!"auto".equals(provider)) {
                    log.warn("Unknown TTS provider '{}' — using 'auto'.", provider);
                }
                // Privacy: answers are only sent to a cloud voice when the conversation
                // already runs on that cloud; with a local model the voice stays local too.
                if (hasOpenAi && "openai".equals(llmProvider)) {
                    chain.add(new OpenAiSpeechSynthesizer(openAiKey, voice));
                }
                chain.add(new WindowsSpeechSynthesizer());
            }
        }
        if (!isWindows()) {
            chain.removeIf(s -> s instanceof WindowsSpeechSynthesizer);
        }
        if (chain.isEmpty()) {
            return new ConsoleSpeechSynthesizer();
        }
        return chain.size() == 1 ? chain.get(0) : new FallbackSpeechSynthesizer(chain);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    public SpeechRecognizer recognizer() {
        return recognizer;
    }

    public SpeakingMonitor speakingMonitor() {
        return speakingMonitor;
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
