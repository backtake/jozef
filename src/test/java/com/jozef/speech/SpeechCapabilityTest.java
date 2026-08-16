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
                new SpeechProperties.SttProperties("vosk", "/nonexistent/model"), null, null);

        SpeechCapability capability = new SpeechCapability(props, new WakeWordProperties(true, "hej jozef"));

        assertThat(capability.recognizer().name()).isEqualTo("console-input");
        assertThat(capability.wakeWordDetector()).isNull();
    }

    @Test
    void disablesWakeWordWhenSttIsConsole() {
        SpeechProperties props = new SpeechProperties(
                new SpeechProperties.SttProperties("console", null), null, null);

        SpeechCapability capability = new SpeechCapability(props, new WakeWordProperties(true, "hej jozef"));

        assertThat(capability.recognizer().name()).isEqualTo("console-input");
        assertThat(capability.wakeWordDetector()).isNull();
    }

    @Test
    void usesVoskAndWakeWordWhenModelAvailable(@TempDir Path modelDir) throws IOException {
        Files.writeString(modelDir.resolve("README"), "model"); // make the dir non-empty

        SpeechProperties props = new SpeechProperties(
                new SpeechProperties.SttProperties("vosk", modelDir.toString()), null, null);

        SpeechCapability capability = new SpeechCapability(props, new WakeWordProperties(true, "hej jozef"));

        assertThat(capability.recognizer().name()).isEqualTo("vosk");
        assertThat(capability.wakeWordDetector()).isNotNull();
        assertThat(capability.wakeWordDetector().name()).isEqualTo("vosk-wake-word");
    }
}
