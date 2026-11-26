package com.jozef.channels.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Minimal Telegram Bot API client (long polling): just what Jozef needs to
 * receive text messages and answer them. Outbound HTTPS only — no open ports.
 */
public class TelegramBotApi {

    /**
     * @param dateEpochSeconds when the message was sent (0 if unknown)
     * @param privateChat      a one-to-one chat (groups are never served)
     * @param forwarded        forwarded from someone else — data, not the owner's command
     */
    public record Update(long updateId, long chatId, String text, String fromName,
                         long dateEpochSeconds, boolean privateChat, boolean forwarded) {
        public Update(long updateId, long chatId, String text, String fromName) {
            this(updateId, chatId, text, fromName, System.currentTimeMillis() / 1000, true, false);
        }
    }

    private static final int LONG_POLL_SECONDS = 50;

    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public TelegramBotApi(String token) {
        this("https://api.telegram.org/bot" + token, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    TelegramBotApi(String baseUrl, HttpClient http) {
        this.baseUrl = baseUrl;
        this.http = http;
    }

    /** Blocks up to ~50 s waiting for new messages after {@code offset}. */
    public List<Update> getUpdates(long offset) throws Exception {
        return getUpdates(offset, LONG_POLL_SECONDS);
    }

    /**
     * Tells Telegram everything before {@code offset} was received, so a crash while
     * handling a batch never redelivers (and re-runs) it.
     */
    public void acknowledge(long offset) throws Exception {
        getUpdates(offset, 0);
    }

    private List<Update> getUpdates(long offset, int timeoutSeconds) throws Exception {
        String url = baseUrl + "/getUpdates?timeout=" + timeoutSeconds + "&offset=" + offset
                + "&allowed_updates=%5B%22message%22%5D";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(LONG_POLL_SECONDS + 15))
                .GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Telegram getUpdates HTTP " + response.statusCode());
        }
        return parseUpdates(response.body());
    }

    List<Update> parseUpdates(String body) throws Exception {
        JsonNode root = mapper.readTree(body);
        List<Update> updates = new ArrayList<>();
        for (JsonNode u : root.path("result")) {
            long id = u.path("update_id").asLong();
            JsonNode message = u.path("message");
            if (message.isMissingNode()) {
                updates.add(new Update(id, 0, null, null, 0, false, false));
                continue;
            }
            boolean forwarded = message.has("forward_origin") || message.has("forward_from")
                    || message.has("forward_from_chat") || message.has("forward_date");
            updates.add(new Update(id,
                    message.path("chat").path("id").asLong(),
                    message.path("text").isTextual() ? message.path("text").asText() : null,
                    message.path("from").path("first_name").asText(""),
                    message.path("date").asLong(0),
                    "private".equals(message.path("chat").path("type").asText("")),
                    forwarded));
        }
        return updates;
    }

    public void sendMessage(long chatId, String text) throws Exception {
        String safe = text == null || text.isBlank() ? "…" : text;
        if (safe.length() > 4000) safe = safe.substring(0, 3997) + "…"; // Telegram limit is 4096
        String json = mapper.writeValueAsString(Map.of("chat_id", chatId, "text", safe));
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/sendMessage"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Telegram sendMessage HTTP " + response.statusCode());
        }
    }
}
