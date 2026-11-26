package com.jozef.speech.tts;

import com.jozef.speech.SpeechSynthesizer;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Offline Windows text-to-speech using the built-in System.Speech engine
 * (invoked through PowerShell). No network, no key — the always-available fallback.
 *
 * The text travels Base64-encoded, so no quoting/escaping of user or model text
 * ever reaches the PowerShell command line. A voice matching {@code culture}
 * (default pl-PL, e.g. "Microsoft Paulina") is preferred when installed.
 */
@Slf4j
public class WindowsSpeechSynthesizer implements SpeechSynthesizer {

    private static final long MAX_SPEAK_SECONDS = 120;

    private final String culture;

    public WindowsSpeechSynthesizer() {
        this("pl-PL");
    }

    public WindowsSpeechSynthesizer(String culture) {
        this.culture = culture == null || !culture.matches("[a-zA-Z]{2}-[a-zA-Z]{2}") ? "pl-PL" : culture;
    }

    @Override
    public String name() {
        return "windows";
    }

    @Override
    public void speak(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        try {
            Process process = new ProcessBuilder(
                    "powershell", "-NoProfile", "-NonInteractive", "-Command", stdinScript())
                    .redirectErrorStream(true)
                    .start();
            // The text goes in through stdin as UTF-8 bytes: no command-line length limit
            // (a long e-mail read aloud) and nothing user-written on the command line.
            try (var stdin = process.getOutputStream()) {
                stdin.write(text.getBytes(StandardCharsets.UTF_8));
            }
            process.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            if (!process.waitFor(MAX_SPEAK_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("Windows TTS timed out");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("Windows TTS exited with " + process.exitValue());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Windows TTS interrupted", e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Windows TTS failed: " + e.getMessage(), e);
        }
    }

    /** Reads raw UTF-8 bytes from stdin ([Console]::In would decode with the OEM code page). */
    String stdinScript() {
        return "$in = [Console]::OpenStandardInput(); $ms = New-Object IO.MemoryStream; $in.CopyTo($ms); "
                + "$t = [Text.Encoding]::UTF8.GetString($ms.ToArray()); "
                + "Add-Type -AssemblyName System.Speech; "
                + "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; "
                + "$v = $s.GetInstalledVoices() | Where-Object { $_.Enabled -and $_.VoiceInfo.Culture.Name -eq '"
                + culture + "' } | Select-Object -First 1; "
                + "if ($v) { $s.SelectVoice($v.VoiceInfo.Name) }; "
                + "$s.Speak($t)";
    }

    /** Older variant (text Base64-encoded inside the command) — kept for tests/reference. */
    String script(String text) {
        String b64 = Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
        return "Add-Type -AssemblyName System.Speech; "
                + "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; "
                + "$v = $s.GetInstalledVoices() | Where-Object { $_.Enabled -and $_.VoiceInfo.Culture.Name -eq '"
                + culture + "' } | Select-Object -First 1; "
                + "if ($v) { $s.SelectVoice($v.VoiceInfo.Name) }; "
                + "$s.Speak([Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('" + b64 + "')))";
    }
}
