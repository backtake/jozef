package com.jozef.core.security;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ConfirmationReplyTest {

    @ParameterizedTest
    @ValueSource(strings = {"tak", "Tak.", "TAK!", "tak, wyślij", "Jasne", "ok", "potwierdzam",
            "dawaj", "zrób to", "Józef, tak", "no tak", "yes"})
    void recognisesYes(String reply) {
        assertThat(ConfirmationReply.classify(reply)).isEqualTo(ConfirmationReply.Kind.YES);
    }

    @ParameterizedTest
    @ValueSource(strings = {"nie", "Nie.", "anuluj", "nie wysyłaj", "stop", "cancel"})
    void recognisesNo(String reply) {
        assertThat(ConfirmationReply.classify(reply)).isEqualTo(ConfirmationReply.Kind.NO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "no", "tak ale zmień temat na urlop", "nie wiem",
            "jaka jest pogoda", "wyślij do Marka zamiast Ani", "tak sobie myślę że nie"})
    void anythingElseIsNotAnAnswer(String reply) {
        assertThat(ConfirmationReply.classify(reply)).isEqualTo(ConfirmationReply.Kind.OTHER);
    }
}
