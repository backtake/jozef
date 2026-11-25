package com.jozef.speech;

import com.jozef.configuration.SpeechProperties;
import com.jozef.configuration.WakeWordProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SpeechCapabilityTest {

    @Test
    void fallsBackToConsoleWhenVoskModelMissing() {
        SpeechProperties props = new SpeechProperties(
                new SpeechProperties.SttProperties("vosk", "/nonexistent/model", false), null, null);

        SpeechCapability capability = new SpeechCapability(props, new WakeWordProperties(true, "hej jozef"));

        assertThat(capability.recognizer().name()).isEqualTo("console-input");
        assertThat(capability.wakeWordDetector()).isNull();
    }

    @Test
    void autoTtsPrefersOpenAiWhenKeyPresentAndKeepsOfflineFallback() {
        SpeechProperties props = new SpeechProperties(
                new SpeechProperties.SttProperties("console", null, false),
                new SpeechProperties.TtsProperties("auto", "ash"), null);

        SpeechCapability capability = new SpeechCapability(props, new WakeWordProperties(false, "x"), "openai",
                name -> "OPENAI_API_KEY".equals(name) ? "sk-test" : null);

        assertThat(capability.synthesizer().name()).startsWith("openai");
    }

    @Test
    void autoTtsStaysLocalWhenTheConversationRunsOnALocalModel() {
        SpeechProperties props = new SpeechProperties(
                new SpeechProperties.SttProperties("console", null, false),
                new SpeechProperties.TtsProperties("auto", "ash"), null);

        SpeechCapability capability = new SpeechCapability(props, new WakeWordProperties(false, "x"), "ollama",
                name -> "OPENAI_API_KEY".equals(name) ? "sk-test" : null);

        assertThat(capability.synthesizer().name()).doesNotContain("openai");
    }

    @Test
    void legacyEdgeProviderDegradesToAutoInsteadOfBreaking() {
        SpeechProperties props = new SpeechProperties(
                new SpeechProperties.SttProperties("console", null, false),
                new SpeechProperties.TtsProperties("edge", "pl-PL-ZofiaNeural"), null);

        SpeechCapability capability = new SpeechCapability(props, new WakeWordProperties(false, "x"), "ollama", name -> null);

        assertThat(capability.synthesizer().name()).doesNotContain("edge");
    }

    @Test
    void disablesWakeWordWhenSttIsConsole() {
        SpeechProperties props = new SpeechProperties(
                new SpeechProperties.SttProperties("console", null, false), null, null);

        SpeechCapability capability = new SpeechCapability(props, new WakeWordProperties(true, "hej jozef"));

        assertThat(capability.recognizer().name()).isEqualTo("console-input");
        assertThat(capability.wakeWordDetector()).isNull();
    }

    @Test
    void usesVoskAndWakeWordWhenModelAvailable(@TempDir Path modelDir) throws IOException {
        Files.createDirectories(modelDir.resolve("am")); // a valid Vosk model has an "am" dir

        SpeechProperties props = new SpeechProperties(
                new SpeechProperties.SttProperties("vosk", modelDir.toString(), false), null, null);

        SpeechCapability capability = new SpeechCapability(props, new WakeWordProperties(true, "hej jozef"));

        assertThat(capability.recognizer().name()).isEqualTo("vosk");
        assertThat(capability.wakeWordDetector()).isNotNull();
        assertThat(capability.wakeWordDetector().name()).isEqualTo("vosk-wake-word");
    }
}
