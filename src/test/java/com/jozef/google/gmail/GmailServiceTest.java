package com.jozef.google.gmail;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GmailServiceTest {

    @Test
    void buildsRfc822MimeMessage() {
        String mime = GmailService.buildMimeMessage("user@example.com", "Hello", "Body line");

        assertThat(mime).contains("To: user@example.com");
        assertThat(mime).contains("Subject: Hello");
        assertThat(mime).contains("Content-Type: text/plain; charset=UTF-8");
        assertThat(mime).contains("Content-Transfer-Encoding: base64");
        String encodedBody = mime.substring(mime.indexOf("\r\n\r\n") + 4);
        assertThat(new String(java.util.Base64.getMimeDecoder().decode(encodedBody), java.nio.charset.StandardCharsets.UTF_8))
                .isEqualTo("Body line");
    }

    @Test
    void headerInjectionCannotAddHiddenRecipients() {
        String mime = GmailService.buildMimeMessage("ania@example.com\r\nBcc: attacker@evil.example",
                "Urlop\r\nBcc: attacker@evil.example", "x");
        String headers = mime.substring(0, mime.indexOf("\r\n\r\n"));

        assertThat(headers.lines()).noneMatch(l -> l.toLowerCase().startsWith("bcc:"));
    }

    @Test
    void polishSubjectIsEncodedPerRfc2047() {
        String mime = GmailService.buildMimeMessage("a@b.pl", "Spóźnię się", "x");
        assertThat(mime).contains("Subject: =?UTF-8?B?");
        assertThat(mime).doesNotContain("Spóźnię");
    }

    @Test
    void replyGoesToReplyToInTheSameThread() {
        java.util.Map<String, Object> original = java.util.Map.of(
                "threadId", "t-1",
                "payload", java.util.Map.of("headers", java.util.List.of(
                        java.util.Map.of("name", "From", "value", "Szef <szef@firma.pl>"),
                        java.util.Map.of("name", "Reply-To", "value", "biuro@firma.pl"),
                        java.util.Map.of("name", "Subject", "value", "Raport"),
                        java.util.Map.of("name", "Message-ID", "value", "<abc@mail>"))));

        GmailService.Reply reply = GmailService.replyFor(original);

        assertThat(reply.to()).isEqualTo("biuro@firma.pl");
        assertThat(reply.subject()).isEqualTo("Re: Raport");
        assertThat(reply.threadId()).isEqualTo("t-1");
        assertThat(GmailService.buildMimeMessage(reply.to(), reply.subject(), "ok", reply.inReplyTo()))
                .contains("In-Reply-To: <abc@mail>").contains("References: <abc@mail>");
    }

    @Test
    void base64urlRoundTripsUnicode() {
        String original = "Cześć Jozef 👋";
        String encoded = GmailService.encodeBase64Url(original);

        assertThat(encoded).doesNotContain("+").doesNotContain("/").doesNotContain("=");
        assertThat(GmailService.decodeBase64Url(encoded)).isEqualTo(original);
    }

    @Test
    void parsesMessageMetadata() {
        Map<String, Object> json = Map.of(
                "id", "m1",
                "threadId", "t1",
                "snippet", "Hello there",
                "payload", Map.of(
                        "headers", List.of(
                                Map.of("name", "From", "value", "Alice <alice@example.com>"),
                                Map.of("name", "Subject", "value", "Standup notes"))));

        GmailMessage message = GmailService.toMessage(json);

        assertThat(message.id()).isEqualTo("m1");
        assertThat(message.threadId()).isEqualTo("t1");
        assertThat(message.from()).isEqualTo("Alice <alice@example.com>");
        assertThat(message.subject()).isEqualTo("Standup notes");
        assertThat(message.snippet()).isEqualTo("Hello there");
        assertThat(message.body()).isNull();
    }

    @Test
    void decodesPlainTextBodyFromPayload() {
        String body = GmailService.encodeBase64Url("Treść wiadomości");
        Map<String, Object> json = Map.of(
                "id", "m2",
                "payload", Map.of(
                        "parts", List.of(
                                Map.of("mimeType", "text/plain", "body", Map.of("data", body)))));

        GmailMessage message = GmailService.toMessage(json);

        assertThat(message.body()).isEqualTo("Treść wiadomości");
    }
}
