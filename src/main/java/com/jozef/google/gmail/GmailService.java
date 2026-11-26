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
        return buildMimeMessage(to, subject, body, null);
    }

    /**
     * Builds the RFC 5322 message. Header values come from the model, so line breaks
     * are stripped (otherwise "temat\r\nBcc: obcy@x" would add a hidden recipient), the
     * subject is RFC 2047-encoded (Polish letters) and the body travels as base64.
     */
    static String buildMimeMessage(String to, String subject, String body, String inReplyTo) {
        StringBuilder sb = new StringBuilder();
        sb.append("To: ").append(encodeAddress(headerValue(to))).append("\r\n");
        sb.append("Subject: ").append(encodeHeaderWord(headerValue(subject))).append("\r\n");
        if (inReplyTo != null && !inReplyTo.isBlank()) {
            String id = headerValue(inReplyTo);
            sb.append("In-Reply-To: ").append(id).append("\r\n");
            sb.append("References: ").append(id).append("\r\n");
        }
        sb.append("MIME-Version: 1.0\r\n");
        sb.append("Content-Type: text/plain; charset=UTF-8\r\n");
        sb.append("Content-Transfer-Encoding: base64\r\n");
        sb.append("\r\n");
        sb.append(Base64.getMimeEncoder().encodeToString((body == null ? "" : body).getBytes(StandardCharsets.UTF_8)));
        return sb.toString();
    }

    /** A single-line header value: CR/LF and other control characters removed. */
    static String headerValue(String value) {
        if (value == null) return "";
        return value.replaceAll("[\\r\\n\\u0000-\\u001f\\u007f]+", " ").trim();
    }

    /**
     * RFC 2047 encoded-words, each at most 75 characters (≤ 45 UTF-8 bytes of text, never
     * splitting a character), folded with CRLF + space. ASCII text is returned unchanged.
     */
    static String encodeHeaderWord(String value) {
        boolean ascii = value.chars().allMatch(c -> c >= 0x20 && c < 0x7f);
        if (ascii) return value;
        StringBuilder out = new StringBuilder();
        StringBuilder chunk = new StringBuilder();
        int chunkBytes = 0;
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            int bytes = ch.getBytes(StandardCharsets.UTF_8).length;
            if (chunkBytes + bytes > 45) {
                appendWord(out, chunk.toString());
                chunk.setLength(0);
                chunkBytes = 0;
            }
            chunk.append(ch);
            chunkBytes += bytes;
            i += Character.charCount(cp);
        }
        appendWord(out, chunk.toString());
        return out.toString();
    }

    private static void appendWord(StringBuilder out, String text) {
        if (text.isEmpty()) return;
        if (out.length() > 0) out.append("\r\n ");
        out.append("=?UTF-8?B?").append(Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8))).append("?=");
    }

    /** "Józef Kowalski <j@x.pl>" → encoded display name + raw address. */
    static String encodeAddress(String address) {
        int lt = address.lastIndexOf('<');
        if (lt > 0 && address.endsWith(">")) {
            String name = address.substring(0, lt).trim().replaceAll("^\"|\"$", "");
            return encodeHeaderWord(name) + " " + address.substring(lt);
        }
        return address;
    }

    /**
     * Replies in the original thread: To = Reply-To (or From), Subject "Re: …",
     * In-Reply-To/References set so mail clients thread it. Returns the new message id.
     */
    public String replyToMessage(String messageId, String body) {
        Map<String, Object> original = getJson(BASE + "/messages/" + urlEncode(messageId)
                + "?format=metadata&metadataHeaders=From&metadataHeaders=Reply-To"
                + "&metadataHeaders=Subject&metadataHeaders=Message-ID");
        Reply reply = replyFor(original);
        String raw = buildMimeMessage(reply.to(), reply.subject(), body, reply.inReplyTo());
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("raw", encodeBase64Url(raw));
        if (reply.threadId() != null) payload.put("threadId", reply.threadId());
        return (String) postJson(BASE + "/messages/send", payload).get("id");
    }

    /** Who/what a reply goes to — also used to tell the user before they confirm. */
    public Reply replyTarget(String messageId) {
        return replyFor(getJson(BASE + "/messages/" + urlEncode(messageId)
                + "?format=metadata&metadataHeaders=From&metadataHeaders=Reply-To"
                + "&metadataHeaders=Subject&metadataHeaders=Message-ID"));
    }

    public record Reply(String to, String subject, String inReplyTo, String threadId) {}

    @SuppressWarnings("unchecked")
    static Reply replyFor(Map<String, Object> original) {
        Map<String, Object> payload = (Map<String, Object>) original.get("payload");
        String replyTo = header(payload, "Reply-To");
        String to = replyTo != null && !replyTo.isBlank() ? replyTo : header(payload, "From");
        String subject = header(payload, "Subject");
        subject = subject == null ? "" : subject;
        if (!subject.regionMatches(true, 0, "Re:", 0, 3)) subject = "Re: " + subject;
        return new Reply(to, subject, header(payload, "Message-ID"), (String) original.get("threadId"));
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
