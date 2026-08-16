package com.jozef.google.calendar;

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
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Google Calendar API v3, wrapped behind plain methods so the agent tools stay
 * trivial. Authentication is inherited from {@link GoogleAuthService} (lazy).
 */
@Slf4j
public class GoogleCalendarService {

    private static final String BASE = "https://www.googleapis.com/calendar/v3";

    private final GoogleAuthService auth;
    private final String calendarId;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public GoogleCalendarService(GoogleAuthService auth, String calendarId) {
        this.auth = auth;
        this.calendarId = (calendarId == null || calendarId.isBlank()) ? "primary" : calendarId;
        this.httpClient = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
    }

    /** Upcoming events from now onwards, sorted by start time. */
    public List<CalendarEvent> listUpcoming(int maxResults) {
        String url = BASE + "/calendars/" + urlEncode(calendarId) + "/events"
                + "?timeMin=" + urlEncode(Instant.now().toString())
                + "&maxResults=" + Math.max(1, maxResults)
                + "&orderBy=startTime"
                + "&singleEvents=true";

        Map<String, Object> json = getJson(url);
        List<CalendarEvent> events = new ArrayList<>();
        Object raw = json.get("items");
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    events.add(toEvent((Map<String, Object>) m));
                }
            }
        }
        return events;
    }

    /** Creates an event and returns it (with Google-assigned id and link). */
    public CalendarEvent createEvent(String summary, String startIso, String endIso,
                                     String description, String location) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("summary", summary);
        if (location != null && !location.isBlank()) {
            body.put("location", location);
        }
        if (description != null && !description.isBlank()) {
            body.put("description", description);
        }
        String tz = ZoneId.systemDefault().getId();
        body.put("start", Map.of("dateTime", startIso, "timeZone", tz));
        body.put("end", Map.of("dateTime", endIso, "timeZone", tz));

        Map<String, Object> created = postJson(
                BASE + "/calendars/" + urlEncode(calendarId) + "/events", body);
        return toEvent(created);
    }

    /** Deletes an event by id. Throws on failure. */
    public void deleteEvent(String eventId) {
        String url = BASE + "/calendars/" + urlEncode(calendarId) + "/events/" + urlEncode(eventId);
        HttpRequest request = auth.authorizedRequest(url).DELETE().build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 204 && response.statusCode() != 200) {
                throw new IllegalStateException("Google Calendar delete failed ("
                        + response.statusCode() + "): " + response.body());
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Google Calendar delete failed: " + e.getMessage(), e);
        }
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
            throw new IllegalStateException("Cannot serialize event body", e);
        }
    }

    private Map<String, Object> sendJson(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Google Calendar API error ("
                        + response.statusCode() + "): " + response.body());
            }
            if (response.body() == null || response.body().isBlank()) {
                return Map.of();
            }
            return mapper.readValue(response.body(), new TypeReference<>() {});
        } catch (IOException e) {
            throw new IllegalStateException("Google Calendar API call failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Google Calendar API call interrupted", e);
        }
    }

    // ─── Mapping (package-private for tests) ───────────────────

    static CalendarEvent toEvent(Map<String, Object> m) {
        return new CalendarEvent(
                (String) m.get("id"),
                (String) m.get("summary"),
                dateTime(m.get("start")),
                dateTime(m.get("end")),
                (String) m.get("description"),
                (String) m.get("location"),
                (String) m.get("htmlLink"));
    }

    static String dateTime(Object value) {
        if (value instanceof Map<?, ?> m) {
            Object dt = m.get("dateTime");
            if (dt != null) {
                return String.valueOf(dt);
            }
            Object d = m.get("date");
            if (d != null) {
                return String.valueOf(d);
            }
        }
        return null;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
