package com.jozef.speech.tts;

import com.jozef.speech.SpeechSynthesizer;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Free, high-quality neural text-to-speech using Microsoft Edge's online TTS
 * (the same voices used by the Edge "Read Aloud" feature). No API key required.
 *
 * Implemented in pure Java over WebSocket. Default Polish voice: pl-PL-ZofiaNeural.
 */
@Slf4j
public class EdgeTtsSynthesizer implements SpeechSynthesizer {

    private static final String TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4";
    private static final String SEC_MS_GEC_VERSION = "1-143.0.3650.75";
    private static final String WSS_BASE =
            "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1";
    private static final long WIN_EPOCH_SECONDS = 11_644_473_600L;
    private static final String OUTPUT_FORMAT = "audio-24khz-48kbitrate-mono-mp3";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern(
            "EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'", Locale.US);

    private final String voice;
    private final HttpClient client;

    public EdgeTtsSynthesizer(String voice) {
        this.voice = (voice == null || voice.isBlank() || "default".equalsIgnoreCase(voice))
                ? "pl-PL-ZofiaNeural"
                : voice;
        this.client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build();
    }

    @Override
    public String name() {
        return "edge";
    }

    @Override
    public void speak(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        try {
            byte[] mp3 = render(text);
            playMp3(mp3);
        } catch (Exception e) {
            log.warn("Edge TTS failed ({}), falling back to console", e.getMessage());
            System.out.println("🔊 Jozef: " + text);
        }
    }

    /** Render text to an MP3 byte array via Edge TTS (exposed for testing). */
    public byte[] render(String text) throws Exception {
        String url = WSS_BASE
                + "?TrustedClientToken=" + TRUSTED_CLIENT_TOKEN
                + "&ConnectionId=" + UUID.randomUUID().toString().replace("-", "")
                + "&Sec-MS-GEC=" + secMsGec()
                + "&Sec-MS-GEC-Version=" + SEC_MS_GEC_VERSION;

        ByteArrayOutputStream audio = new ByteArrayOutputStream();
        CompletableFuture<byte[]> done = new CompletableFuture<>();

        WebSocket.Listener listener = new WebSocket.Listener() {
            @Override
            public void onOpen(WebSocket ws) {
                ws.request(1);
                ws.sendText(speechConfigMessage(), true);
                ws.sendText(ssmlMessage(text), true);
            }

            @Override
            public CompletableFuture<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
                int size = data.remaining();
                byte[] payload = extractAudioPayload(data);
                log.debug("Edge TTS binary frame: size={}, payload={}", size, payload.length);
                if (payload.length > 0) {
                    audio.writeBytes(payload);
                }
                ws.request(1);
                return null;
            }

            @Override
            public CompletableFuture<?> onText(WebSocket ws, CharSequence data, boolean last) {
                log.debug("Edge TTS text frame: {}", data);
                // The server keeps the socket open after streaming; it signals the end
                // of the audio stream with a text frame containing "Path:turn.end".
                if (data.toString().toLowerCase().contains("turn.end")) {
                    done.complete(audio.toByteArray());
                }
                ws.request(1);
                return null;
            }

            @Override
            public CompletableFuture<?> onClose(WebSocket ws, int statusCode, String reason) {
                log.debug("Edge TTS closed: status={}, reason={}", statusCode, reason);
                done.complete(audio.toByteArray());
                return null;
            }

            @Override
            public void onError(WebSocket ws, Throwable error) {
                log.warn("Edge TTS websocket error", error);
                done.completeExceptionally(error);
            }
        };

        WebSocket ws = client.newWebSocketBuilder()
                .header("Pragma", "no-cache")
                .header("Cache-Control", "no-cache")
                .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
                .header("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0")
                .header("Accept-Language", "en-US,en;q=0.9")
                .buildAsync(URI.create(url), listener)
                .join();

        try {
            byte[] result = done.get(25, TimeUnit.SECONDS);
            if (result.length == 0) {
                throw new IllegalStateException("No audio received from Edge TTS");
            }
            return result;
        } finally {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(2, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                // already closed
            }
        }
    }

    /** The first 2 bytes are the header length; after the header there is CRLF + raw audio. */
    private byte[] extractAudioPayload(ByteBuffer data) {
        byte[] all = new byte[data.remaining()];
        data.get(all);
        if (all.length < 2) {
            return new byte[0];
        }
        int headerLength = ((all[0] & 0xFF) << 8) | (all[1] & 0xFF);
        int audioStart = 2 + headerLength + 2; // header + CRLF separator
        if (audioStart >= all.length) {
            return new byte[0];
        }
        byte[] audio = new byte[all.length - audioStart];
        System.arraycopy(all, audioStart, audio, 0, audio.length);
        return audio;
    }

    private String speechConfigMessage() {
        return "X-Timestamp:" + dateString() + "\r\n"
                + "Content-Type:application/json; charset=utf-8\r\n"
                + "Path:speech.config\r\n\r\n"
                + "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{"
                + "\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"},"
                + "\"outputFormat\":\"" + OUTPUT_FORMAT + "\"}}}}\r\n";
    }

    private String ssmlMessage(String text) {
        String escaped = escapeXml(text);
        String ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='pl-PL'>"
                + "<voice name='" + voice + "'>"
                + "<prosody pitch='+0Hz' rate='+0%' volume='+0%'>"
                + escaped
                + "</prosody></voice></speak>";
        return "X-RequestId:" + UUID.randomUUID().toString().replace("-", "") + "\r\n"
                + "Content-Type:application/ssml+xml\r\n"
                + "X-Timestamp:" + dateString() + "Z\r\n"
                + "Path:ssml\r\n\r\n"
                + ssml;
    }

    private String dateString() {
        return ZonedDateTime.now(ZoneOffset.UTC).format(DATE_FMT);
    }

    private String secMsGec() throws Exception {
        long unixSeconds = Instant.now().getEpochSecond();
        long ticks = unixSeconds + WIN_EPOCH_SECONDS;
        ticks -= ticks % 300; // round down to nearest 5 minutes
        ticks *= 10_000_000L; // to 100-nanosecond intervals

        String toHash = ticks + TRUSTED_CLIENT_TOKEN;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(toHash.getBytes(StandardCharsets.US_ASCII));

        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString().toUpperCase();
    }

    private String escapeXml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private void playMp3(byte[] mp3) throws Exception {
        try (java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(mp3)) {
            javazoom.jl.player.Player player = new javazoom.jl.player.Player(in);
            player.play(); // blocks until the audio finishes
        }
    }
}
