package com.jozef.speech;

import com.jozef.configuration.SpeechProperties;
import com.jozef.core.capabilities.Capability;
import com.jozef.speech.console.ConsoleInputRecognizer;
import com.jozef.speech.console.ConsoleSpeechSynthesizer;
import com.jozef.speech.tts.EdgeTtsSynthesizer;
import com.jozef.speech.tts.WindowsSpeechSynthesizer;
import lombok.extern.slf4j.Slf4j;

/**
 * Provides SpeechRecognizer (STT) and SpeechSynthesizer (TTS) to the system,
 * chosen from configuration.
 *
 * STT is currently console-based for development; the interface is replaceable
 * so a local Whisper/Vosk implementation can be dropped in without touching the agent.
 */
@Slf4j
public class SpeechCapability implements Capability {

    private final SpeechRecognizer recognizer;
    private final SpeechSynthesizer synthesizer;

    public SpeechCapability(SpeechProperties props) {
        this.recognizer = createRecognizer(props);
        this.synthesizer = createSynthesizer(props);
    }

    private SpeechRecognizer createRecognizer(SpeechProperties props) {
        // Future: switch on props.stt().provider() → whisper-local / vosk / azure / deepgram
        return new ConsoleInputRecognizer();
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
        log.info("Speech capability initialized: stt={}, tts={}",
                recognizer.name(), synthesizer.name());
    }

    @Override
    public void shutdown() {
        log.info("Speech capability shut down");
    }
}
