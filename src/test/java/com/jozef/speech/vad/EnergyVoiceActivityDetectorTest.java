package com.jozef.speech.vad;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class EnergyVoiceActivityDetectorTest {

    private static final int FRAME_BYTES = 3200; // 100 ms @ 16 kHz / 16-bit mono

    @Test
    void computesRms() {
        assertThat(EnergyVoiceActivityDetector.rms(silence(FRAME_BYTES), FRAME_BYTES)).isEqualTo(0.0);
        assertThat(EnergyVoiceActivityDetector.rms(tone(2000, FRAME_BYTES), FRAME_BYTES))
                .isCloseTo(2000.0, within(1.0));
    }

    @Test
    void convertsBytesToMillis() {
        assertThat(EnergyVoiceActivityDetector.millisForBytes(3200)).isEqualTo(100);
        assertThat(EnergyVoiceActivityDetector.millisForBytes(0)).isEqualTo(0);
    }

    @Test
    void detectsUtteranceAfterSpeechThenSilence() {
        EnergyVoiceActivityDetector vad = new EnergyVoiceActivityDetector(500, 120, 300);
        feed(vad, tone(2000, FRAME_BYTES), 5); // 500 ms of speech
        feed(vad, silence(FRAME_BYTES), 3);    // 300 ms of silence

        assertThat(vad.utteranceEnded()).isTrue();
    }

    @Test
    void ignoresShortClick() {
        EnergyVoiceActivityDetector vad = new EnergyVoiceActivityDetector(500, 120, 300);
        feed(vad, tone(2000, FRAME_BYTES), 1); // 100 ms click
        feed(vad, silence(FRAME_BYTES), 3);    // silence

        assertThat(vad.utteranceEnded()).isFalse();
    }

    @Test
    void sensitivityFactoryProducesDetectors() {
        assertThat(EnergyVoiceActivityDetector.forSensitivity("high")).isNotNull();
        assertThat(EnergyVoiceActivityDetector.forSensitivity("MEDIUM")).isNotNull();
        assertThat(EnergyVoiceActivityDetector.forSensitivity("low")).isNotNull();
        assertThat(EnergyVoiceActivityDetector.forSensitivity(null)).isNotNull(); // defaults to medium
    }

    private void feed(EnergyVoiceActivityDetector vad, byte[] frame, int times) {
        for (int i = 0; i < times; i++) {
            vad.update(frame, frame.length);
        }
    }

    private byte[] tone(int sample, int bytes) {
        byte[] b = new byte[bytes];
        for (int i = 0; i + 1 < bytes; i += 2) {
            b[i] = (byte) (sample & 0xFF);
            b[i + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        return b;
    }

    private byte[] silence(int bytes) {
        return new byte[bytes];
    }
}
