package com.jozef.speech.tts;

import com.jozef.speech.SpeechSynthesizer;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tries each synthesizer in order until one speaks. The last resort prints the
 * text, so the user always gets the answer even when every voice fails.
 */
@Slf4j
public class FallbackSpeechSynthesizer implements SpeechSynthesizer {

    private final List<SpeechSynthesizer> chain;

    public FallbackSpeechSynthesizer(List<SpeechSynthesizer> chain) {
        if (chain == null || chain.isEmpty()) {
            throw new IllegalArgumentException("At least one synthesizer is required");
        }
        this.chain = List.copyOf(chain);
    }

    @Override
    public String name() {
        return chain.stream().map(SpeechSynthesizer::name).collect(Collectors.joining("→"));
    }

    @Override
    public void speak(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        for (SpeechSynthesizer synthesizer : chain) {
            try {
                synthesizer.speak(text);
                return;
            } catch (Exception e) {
                log.warn("TTS '{}' failed ({}), trying the next one", synthesizer.name(), e.getMessage());
            }
        }
        System.out.println("🔊 Jozef: " + text);
    }
}
