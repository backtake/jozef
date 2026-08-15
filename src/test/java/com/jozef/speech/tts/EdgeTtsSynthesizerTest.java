package com.jozef.speech.tts;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EdgeTtsSynthesizerTest {

    @Test
    void rendersPolishSpeechToMp3() throws Exception {
        EdgeTtsSynthesizer tts = new EdgeTtsSynthesizer("pl-PL-ZofiaNeural");

        byte[] mp3 = tts.render("Dzień dobry. To jest test syntezy mowy.");

        assertThat(mp3).isNotEmpty();
        assertThat(mp3.length).isGreaterThan(100);
    }
}
