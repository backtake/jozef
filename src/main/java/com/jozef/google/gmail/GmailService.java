package com.jozef.google.gmail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jozef.google.auth.GoogleAuthService;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Gmail API v1, wrapped behind plain methods. Authentication is inherited from
 * {@link GoogleAuthService} (lazy, happens on first use).
 */
@Slf4j
public class GmailService {

    private static final String BASE = "https://gmail.googleapis.com/gmail/v1/users/me";

    private final GoogleAuthService auth;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public GmailService(GoogleAuthService auth) {
        this.auth = auth;
        this.httpClient = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
    }

    /** Sends an email and returns the Gmail message id. */
    public String sendEmail(String to, String subject, String body) {
        String raw = buildMimeMessage(to, subject, body);
        String encoded = encodeBase64Url(raw);
        Map<String, Object> payload = Map.of("raw", encoded);
        Map<String, Object> json = postJson(BASE + "/messages/send", payload);
        return (String) json.get("id");
    }

    /** Lists recent messages (id + from + subject + snippet for each). */
    public List<GmailMessage> listRecent(int maxResults, String query) {
        String url = BASE + "/messages?maxResults=" + Math.max(1, maxResults);
        if (query != null && !query.isBlank()) {
            url += "&q=" + urlEncode(query);
        }
        Map<String, Object> json = getJson(url);

        List<GmailMessage> result = new ArrayList<>();
        Object raw = json.get("messages");
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m && m.get("id") != null) {
                    result.add(fetchMessage((String) m.get("id"), false));
                }
            }
        }
        return result;
    }

    /** Reads a single message including its decoded plain-text body. */
    public GmailMessage readMessage(String id) {
        return fetchMessage(id, true);
    }

    private GmailMessage fetchMessage(String id, boolean full) {
        String url = BASE + "/messages/" + urlEncode(id)
                + (full
                        ? "?format=full"
                        : "?format=metadata&metadataHeaders=From&metadataHeaders=Subject");
        return toMessage(getJson(url));
    }

    // ─── HTTP ───────────────────────────────────────────────────

    private Map<String, Object> getJson(String url) {
        HttpRequest request = auth.authorizedRequest(url).GET().build();
        return sendJson(request);
    }

    private Map<String, Object> postJson(String url, Map<String, Object> body) {
        try {
            String json = mapper.writeValueAsString(body);
            HttpRequest request = auth.authorizedRequest(url)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            return sendJson(request);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize request body", e);
        }
    }

    private Map<String, Object> sendJson(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Gmail API error ("
                        + response.statusCode() + "): " + response.body());
            }
            if (response.body() == null || response.body().isBlank()) {
                return Map.of();
            }
            return mapper.readValue(response.body(), new TypeReference<>() {});
        } catch (IOException e) {
            throw new IllegalStateException("Gmail API call failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gmail API call interrupted", e);
        }
    }

    // ─── Mapping / encoding (package-private for tests) ─────────

    static GmailMessage toMessage(Map<String, Object> json) {
        String id = (String) json.get("id");
        String threadId = (String) json.get("threadId");
        String snippet = (String) json.get("snippet");
        Map<String, Object> payload = (Map<String, Object>) json.get("payload");
        return new GmailMessage(
                id,
                threadId,
                header(payload, "From"),
                header(payload, "Subject"),
                snippet,
                bodyText(payload));
    }

    private static String header(Map<String, Object> payload, String name) {
        if (payload == null) {
            return null;
        }
        Object headers = payload.get("headers");
        if (headers instanceof List<?> list) {
            for (Object h : list) {
                if (h instanceof Map<?, ?> m && name.equalsIgnoreCase((String) m.get("name"))) {
                    return (String) m.get("value");
                }
            }
        }
        return null;
    }

    private static String bodyText(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        Object parts = payload.get("parts");
        if (parts instanceof List<?> list) {
            for (Object p : list) {
                if (p instanceof Map<?, ?> m) {
                    String mime = (String) m.get("mimeType");
                    Map<String, Object> body = (Map<String, Object>) m.get("body");
                    if (mime != null && mime.startsWith("text/plain")
                            && body != null && body.get("data") != null) {
                        return decodeBase64Url((String) body.get("data"));
                    }
                }
            }
            for (Object p : list) {
                if (p instanceof Map<?, ?> m) {
                    Map<String, Object> body = (Map<String, Object>) m.get("body");
                    if (body != null && body.get("data") != null) {
                        return decodeBase64Url((String) body.get("data"));
                    }
                }
            }
        }
        Map<String, Object> body = (Map<String, Object>) payload.get("body");
        if (body != null && body.get("data") != null) {
            return decodeBase64Url((String) body.get("data"));
        }
        return null;
    }

    static String buildMimeMessage(String to, String subject, String body) {
        StringBuilder sb = new StringBuilder();
        sb.append("To: ").append(to == null ? "" : to).append("\r\n");
        sb.append("Subject: ").append(subject == null ? "" : subject).append("\r\n");
        sb.append("MIME-Version: 1.0\r\n");
        sb.append("Content-Type: text/plain; charset=UTF-8\r\n");
        sb.append("\r\n");
        sb.append(body == null ? "" : body);
        return sb.toString();
    }

    static String encodeBase64Url(String raw) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    static String decodeBase64Url(String data) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(data);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Could not decode Gmail body: {}", e.getMessage());
            return null;
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
