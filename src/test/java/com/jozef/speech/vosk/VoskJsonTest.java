package com.jozef.speech.vosk;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VoskJsonTest {

    @Test
    void parsesFinalText() {
        assertThat(VoskJson.text("{\"text\":\"otwórz Chrome\"}")).isEqualTo("otwórz Chrome");
    }

    @Test
    void parsesPartial() {
        assertThat(VoskJson.partial("{\"partial\":\"otwórz\"}")).isEqualTo("otwórz");
    }

    @Test
    void returnsEmptyOnMalformedOrMissing() {
        assertThat(VoskJson.text("not json")).isEmpty();
        assertThat(VoskJson.partial(null)).isEmpty();
        assertThat(VoskJson.text("{\"other\":\"x\"}")).isEmpty();
    }
}
