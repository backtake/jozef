package com.jozef.core.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfirmationReplyTest {

    @Test
    void yesWithTheToolsVerbIsAccepted() {
        assertThat(ConfirmationReply.classify("tak, wyślij", List.of("gmail_send"))).isEqualTo(ConfirmationReply.Kind.YES);
        assertThat(ConfirmationReply.classify("wyślij proszę", List.of("gmail_send"))).isEqualTo(ConfirmationReply.Kind.YES);
        // A correction or an unrelated continuation is not a yes.
        assertThat(ConfirmationReply.classify("tak, wyślij do Basi", List.of("gmail_send"))).isEqualTo(ConfirmationReply.Kind.OTHER);
        assertThat(ConfirmationReply.classify("wyślij do Basi", List.of("gmail_send"))).isEqualTo(ConfirmationReply.Kind.OTHER);
        assertThat(ConfirmationReply.classify("tak w ogóle która godzina")).isEqualTo(ConfirmationReply.Kind.OTHER);
        assertThat(ConfirmationReply.classify("tak sobie")).isEqualTo(ConfirmationReply.Kind.OTHER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"tak", "Tak.", "TAK!", "Jasne", "ok", "potwierdzam",
            "dawaj", "zrób to", "Józef, tak", "no tak", "yes", "tak, oczywiście", "no jasne", "tak jest",
            "oczywiście", "śmiało", "tak tak tak", "dobra"})
    void recognisesYes(String reply) {
        assertThat(ConfirmationReply.classify(reply)).isEqualTo(ConfirmationReply.Kind.YES);
    }

    @ParameterizedTest
    @ValueSource(strings = {"nie", "Nie.", "anuluj", "nie wysyłaj", "stop", "cancel", "jednak nie", "nie teraz",
            "tak, nie, nie"})
    void recognisesNo(String reply) {
        assertThat(ConfirmationReply.classify(reply)).isEqualTo(ConfirmationReply.Kind.NO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "no", "tak ale zmień temat na urlop", "jaka jest pogoda",
            "wyślij do Marka zamiast Ani", "tak sobie myślę że nie", "wyślij",
            "tak, ale najpierw przeczytaj mi jeszcze raz treść tego maila"})
    void anythingElseIsNotAnAnswer(String reply) {
        assertThat(ConfirmationReply.classify(reply)).isEqualTo(ConfirmationReply.Kind.OTHER);
    }

    @Test
    void actionVerbApprovesOnlyTheMatchingKindOfTool() {
        assertThat(ConfirmationReply.classify("wyślij", List.of("gmail_send"))).isEqualTo(ConfirmationReply.Kind.YES);
        assertThat(ConfirmationReply.classify("usuń", List.of("calendar_delete"))).isEqualTo(ConfirmationReply.Kind.YES);
        assertThat(ConfirmationReply.classify("usuń", List.of("calendar_create"))).isEqualTo(ConfirmationReply.Kind.OTHER);
        assertThat(ConfirmationReply.classify("wyślij", List.of("calendar_delete"))).isEqualTo(ConfirmationReply.Kind.OTHER);
    }
}
