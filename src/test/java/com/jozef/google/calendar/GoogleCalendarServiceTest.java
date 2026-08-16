package com.jozef.google.calendar;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleCalendarServiceTest {

    @Test
    void parsesEventFromGoogleJson() {
        Map<String, Object> json = Map.of(
                "id", "evt1",
                "summary", "Standup",
                "description", "Daily sync",
                "location", "Office",
                "htmlLink", "https://calendar.google.com/event?eid=evt1",
                "start", Map.of("dateTime", "2026-08-20T14:00:00+02:00"),
                "end", Map.of("dateTime", "2026-08-20T14:30:00+02:00"));

        CalendarEvent event = GoogleCalendarService.toEvent(json);

        assertThat(event.id()).isEqualTo("evt1");
        assertThat(event.summary()).isEqualTo("Standup");
        assertThat(event.description()).isEqualTo("Daily sync");
        assertThat(event.location()).isEqualTo("Office");
        assertThat(event.htmlLink()).isEqualTo("https://calendar.google.com/event?eid=evt1");
        assertThat(event.start()).isEqualTo("2026-08-20T14:00:00+02:00");
        assertThat(event.end()).isEqualTo("2026-08-20T14:30:00+02:00");
    }

    @Test
    void fallsBackToAllDayDateWhenNoDateTime() {
        Map<String, Object> json = Map.of(
                "id", "evt2",
                "summary", "Holiday",
                "start", Map.of("date", "2026-08-21"),
                "end", Map.of("date", "2026-08-22"));

        CalendarEvent event = GoogleCalendarService.toEvent(json);

        assertThat(event.start()).isEqualTo("2026-08-21");
        assertThat(event.end()).isEqualTo("2026-08-22");
    }

    @Test
    void handlesMissingOptionalFields() {
        CalendarEvent event = GoogleCalendarService.toEvent(Map.of("summary", "Lunch"));

        assertThat(event.summary()).isEqualTo("Lunch");
        assertThat(event.id()).isNull();
        assertThat(event.start()).isNull();
        assertThat(event.location()).isNull();
    }
}
