package com.jozef.speech.tts;

import com.jozef.speech.SpeechSynthesizer;
import lombok.extern.slf4j.Slf4j;

/**
 * Windows text-to-speech using the built-in System.Speech engine
 * (invoked through PowerShell). No additional dependencies.
 */
@Slf4j
public class WindowsSpeechSynthesizer implements SpeechSynthesizer {

    @Override
    public String name() {
        return "windows";
    }

    @Override
    public void speak(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        String escaped = text.replace("'", "''");
        String command = "Add-Type -AssemblyName System.Speech; "
                + "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; "
                + "$s.Speak('" + escaped + "')";

        try {
            Process process = Runtime.getRuntime().exec(new String[]{
                    "powershell", "-NoProfile", "-NonInteractive", "-Command", command
            });
            process.waitFor();
        } catch (Exception e) {
            log.warn("Windows TTS failed: {}", e.getMessage());
            // Fall back to console so the user still sees the response.
            System.out.println("🔊 Jozef: " + text);
        }
    }
}
