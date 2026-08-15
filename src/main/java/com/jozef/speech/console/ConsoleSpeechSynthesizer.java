package com.jozef.speech.console;

import com.jozef.speech.SpeechSynthesizer;
import lombok.extern.slf4j.Slf4j;

/**
 * Temporary: prints text to console instead of speaking.
 * For development until TTS is integrated.
 */
@Slf4j
public class ConsoleSpeechSynthesizer implements SpeechSynthesizer {

    @Override
    public String name() {
        return "console-tts";
    }

    @Override
    public void speak(String text) {
        System.out.println("🔊 Jozef: " + text);
    }
}
