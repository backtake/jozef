package com.jozef.speech.tts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jozef.speech.SpeechSynthesizer;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Neural text-to-speech through the official OpenAI Audio API
 * ({@code /v1/audio/speech}). Natural Polish, low latency, billed per use on
 * the user's own key — a legitimate, supported replacement for the
 * unofficial Edge "Read Aloud" endpoint.
 */
@Slf4j
public class OpenAiSpeechSynthesizer implements SpeechSynthesizer {

    public static final String DEFAULT_ENDPOINT = "https://api.openai.com/v1/audio/speech";
    public static final String DEFAULT_MODEL = "gpt-4o-mini-tts";
    public static final String DEFAULT_VOICE = "ash";

    private final String apiKey;
    private final String model;
    private final String voice;
    private final String endpoint;
    private final String instructions;
    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public OpenAiSpeechSynthesizer(String apiKey, String voice) {
        this(apiKey, DEFAULT_MODEL, voice, DEFAULT_ENDPOINT,
                "Mów naturalnie i ciepło, w tempie rozmowy, jak osobisty asystent.");
    }

    public OpenAiSpeechSynthesizer(String apiKey, String model, String voice, String endpoint, String instructions) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("OpenAI TTS needs an API key");
        }
        this.apiKey = apiKey;
        this.model = model == null || model.isBlank() ? DEFAULT_MODEL : model;
        this.voice = isUsableVoice(voice) ? voice : DEFAULT_VOICE;
        this.endpoint = endpoint == null || endpoint.isBlank() ? DEFAULT_ENDPOINT : endpoint;
        this.instructions = instructions;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /** Edge-style voice ids ("pl-PL-ZofiaNeural") are not OpenAI voices. */
    private static boolean isUsableVoice(String voice) {
        return voice != null && !voice.isBlank() && !voice.contains("-") && !"default".equalsIgnoreCase(voice);
    }

    @Override
    public String name() {
        return "openai";
    }

    @Override
    public void speak(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        // The API takes at most 4096 characters per request — long answers (a whole e-mail)
        // are spoken in sentence-aligned parts.
        for (String part : chunks(text, MAX_CHARS)) {
            Mp3Player.play(render(part));
        }
    }

    static final int MAX_CHARS = 3500;

    static java.util.List<String> chunks(String text, int max) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        String rest = text.trim();
        while (rest.length() > max) {
            int cut = Math.max(rest.lastIndexOf(". ", max), Math.max(rest.lastIndexOf("? ", max), rest.lastIndexOf("! ", max)));
            if (cut < max / 2) cut = rest.lastIndexOf(' ', max);
            if (cut <= 0) cut = max;
            parts.add(rest.substring(0, cut + 1).trim());
            rest = rest.substring(cut + 1).trim();
        }
        if (!rest.isEmpty()) parts.add(rest);
        return parts;
    }

    /** Renders text to MP3 bytes; throws on any failure so a fallback can take over. */
    public byte[] render(String text) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("voice", voice);
            body.put("input", text);
            body.put("response_format", "mp3");
            if (instructions != null && !instructions.isBlank() && model.contains("gpt-4o")) {
                body.put("instructions", instructions);
            }
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                String error = new String(response.body(), StandardCharsets.UTF_8);
                throw new IllegalStateException("OpenAI TTS returned HTTP " + response.statusCode() + ": "
                        + (error.length() > 300 ? error.substring(0, 300) : error));
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenAI TTS interrupted", e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("OpenAI TTS failed: " + e.getMessage(), e);
        }
    }

    /** MP3 playback via JLayer (blocks until finished). */
    static final class Mp3Player {
        private Mp3Player() {}

        static void play(byte[] mp3) {
            try (ByteArrayInputStream in = new ByteArrayInputStream(mp3)) {
                new javazoom.jl.player.Player(in).play();
            } catch (Exception e) {
                throw new IllegalStateException("MP3 playback failed: " + e.getMessage(), e);
            }
        }
    }
}
