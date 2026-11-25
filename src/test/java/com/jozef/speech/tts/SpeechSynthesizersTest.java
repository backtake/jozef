package com.jozef.speech.tts;

import com.jozef.speech.SpeechSynthesizer;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpeechSynthesizersTest {

    private static SpeechSynthesizer recording(String name, List<String> log, boolean fail) {
        return new SpeechSynthesizer() {
            @Override public String name() { return name; }
            @Override public void speak(String text) {
                log.add(name + ":" + text);
                if (fail) throw new IllegalStateException(name + " down");
            }
        };
    }

    @Test
    void fallbackUsesNextSynthesizerWhenOneFails() {
        List<String> log = new ArrayList<>();
        FallbackSpeechSynthesizer chain = new FallbackSpeechSynthesizer(List.of(
                recording("cloud", log, true), recording("local", log, false), recording("never", log, false)));

        chain.speak("Cześć");

        assertThat(log).containsExactly("cloud:Cześć", "local:Cześć");
        assertThat(chain.name()).isEqualTo("cloud→local→never");
    }

    @Test
    void fallbackNeverThrowsEvenWhenAllFail() {
        List<String> log = new ArrayList<>();
        FallbackSpeechSynthesizer chain = new FallbackSpeechSynthesizer(List.of(recording("a", log, true)));
        chain.speak("x");
        assertThat(log).containsExactly("a:x");
    }

    @Test
    void windowsScriptCarriesTextOnlyAsBase64() {
        String hostile = "'); Remove-Item C:\\ -Recurse; ('";
        String script = new WindowsSpeechSynthesizer("pl-PL").script(hostile);

        assertThat(script).doesNotContain("Remove-Item");
        assertThat(script).contains(Base64.getEncoder().encodeToString(hostile.getBytes(StandardCharsets.UTF_8)));
        assertThat(script).contains("'pl-PL'");
    }

    @Test
    void windowsCultureIsValidated() {
        String script = new WindowsSpeechSynthesizer("x'; evil; '").script("a");
        assertThat(script).contains("'pl-PL'").doesNotContain("evil");
    }

    @Test
    void openAiTtsPostsTextAndReturnsAudio() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> body = new AtomicReference<>();
        server.createContext("/v1/audio/speech", ex -> {
            body.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] audio = {(byte) 0xFF, (byte) 0xFB, 1, 2, 3};
            ex.sendResponseHeaders(200, audio.length);
            ex.getResponseBody().write(audio);
            ex.close();
        });
        server.start();
        try {
            OpenAiSpeechSynthesizer tts = new OpenAiSpeechSynthesizer("sk", "gpt-4o-mini-tts", "pl-PL-ZofiaNeural",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/audio/speech", "po polsku");
            byte[] mp3 = tts.render("Dzień dobry");

            assertThat(mp3).hasSize(5);
            assertThat(body.get()).contains("\"input\":\"Dzień dobry\"")
                    .contains("\"voice\":\"ash\"") // Edge voice id replaced by a valid OpenAI voice
                    .contains("\"instructions\":\"po polsku\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void openAiTtsErrorThrowsSoTheChainCanFallBack() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/audio/speech", ex -> {
            byte[] err = "{\"error\":\"quota\"}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(429, err.length);
            ex.getResponseBody().write(err);
            ex.close();
        });
        server.start();
        try {
            OpenAiSpeechSynthesizer tts = new OpenAiSpeechSynthesizer("sk", "tts-1", "nova",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/audio/speech", null);
            assertThatThrownBy(() -> tts.render("x")).hasMessageContaining("429");
        } finally {
            server.stop(0);
        }
    }
}
