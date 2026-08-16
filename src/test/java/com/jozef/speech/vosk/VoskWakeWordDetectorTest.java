package com.jozef.speech.vosk;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VoskWakeWordDetectorTest {

    @Test
    void normalizesCaseDiacriticsAndWhitespace() {
        assertThat(VoskWakeWordDetector.normalize("Hej  JÓZEF ")).isEqualTo("hej jozef");
        assertThat(VoskWakeWordDetector.normalize("  Cześć   ")).isEqualTo("czesc");
        assertThat(VoskWakeWordDetector.normalize(null)).isEmpty();
    }

    @Test
    void detectsWakeWordIgnoringCaseAndDiacritics() {
        assertThat(VoskWakeWordDetector.containsWakeWord("Hej Jozef, otwórz Chrome", "hej jozef")).isTrue();
        assertThat(VoskWakeWordDetector.containsWakeWord("JÓZEF otwórz", "jozef")).isTrue();
        assertThat(VoskWakeWordDetector.containsWakeWord("Dzień dobry", "hej jozef")).isFalse();
    }

    @Test
    void extractsCommandAfterWakeWord() {
        assertThat(VoskWakeWordDetector.commandAfter("Hej Jozef otwórz Chrome", "hej jozef"))
                .isEqualTo("otwórz Chrome");
        assertThat(VoskWakeWordDetector.commandAfter("hej jozef", "hej jozef")).isEmpty();
        assertThat(VoskWakeWordDetector.commandAfter("coś innego", "hej jozef")).isEmpty();
    }
}
