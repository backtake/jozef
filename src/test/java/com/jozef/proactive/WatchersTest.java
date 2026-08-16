package com.jozef.proactive;

import com.jozef.google.calendar.CalendarEvent;
import com.jozef.google.calendar.GoogleCalendarService;
import com.jozef.google.gmail.GmailMessage;
import com.jozef.google.gmail.GmailService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WatchersTest {

    @Test
    void parsesStartTime() {
        assertThat(CalendarWatcher.parseStart("2026-08-20T14:00:00+02:00")).isNotNull();
        assertThat(CalendarWatcher.parseStart("2026-08-20T14:00:00")).isNotNull();
        assertThat(CalendarWatcher.parseStart("")).isNull();
        assertThat(CalendarWatcher.parseStart(null)).isNull();
    }

    @Test
    void notifiesOnlyForEventsStartingSoon() {
        Instant now = Instant.parse("2026-08-20T12:00:00Z");
        Duration lookahead = Duration.ofMinutes(15);

        CalendarEvent soon = event("e1", "2026-08-20T12:10:00Z");
        CalendarEvent later = event("e2", "2026-08-20T20:00:00Z");
        CalendarEvent past = event("e3", "2026-08-20T11:00:00Z");

        assertThat(CalendarWatcher.shouldNotify(soon, now, lookahead)).isTrue();
        assertThat(CalendarWatcher.shouldNotify(later, now, lookahead)).isFalse();
        assertThat(CalendarWatcher.shouldNotify(past, now, lookahead)).isFalse();
    }

    @Test
    void calendarTriggersEachEventOnlyOnce() {
        GoogleCalendarService calendar = mock(GoogleCalendarService.class);
        ProactiveAgentService agent = mock(ProactiveAgentService.class);
        String soon = Instant.now().plus(Duration.ofMinutes(5)).toString();
        when(calendar.listUpcoming(20)).thenReturn(List.of(event("e1", soon)));

        CalendarWatcher watcher = new CalendarWatcher(calendar, agent, Duration.ofMinutes(15));
        watcher.check();
        watcher.check();

        verify(agent, times(1)).trigger(contains("Spotkanie"));
    }

    @Test
    void gmailTriggersEachMessageOnlyOnce() {
        GmailService gmail = mock(GmailService.class);
        ProactiveAgentService agent = mock(ProactiveAgentService.class);
        GmailMessage message = new GmailMessage("m1", "t1", "Alice", "Temat", "snippet", null);
        when(gmail.listRecent(10, "is:unread")).thenReturn(List.of(message));

        GmailWatcher watcher = new GmailWatcher(gmail, agent, 10);
        watcher.check();
        watcher.check();

        verify(agent, times(1)).trigger(contains("Alice"));
    }

    private static CalendarEvent event(String id, String start) {
        return new CalendarEvent(id, "Spotkanie", start, null, null, null, null);
    }
}
