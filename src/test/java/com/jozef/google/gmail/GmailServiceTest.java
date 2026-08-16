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
        assertThat(mime).endsWith("\r\n\r\nBody line");
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
