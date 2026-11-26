package com.jozef.proactive;

import com.jozef.google.calendar.CalendarEvent;
import com.jozef.google.calendar.GoogleCalendarService;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Polls Google Calendar and proactively reminds about events that start soon.
 * Each event is announced only once per run (tracked by id).
 */
@Slf4j
public class CalendarWatcher {

    private final GoogleCalendarService calendar;
    private final ProactiveAgentService agent;
    private final Duration lookahead;
    private final Set<String> notified = ConcurrentHashMap.newKeySet();

    public CalendarWatcher(GoogleCalendarService calendar, ProactiveAgentService agent, Duration lookahead) {
        this.calendar = calendar;
        this.agent = agent;
        this.lookahead = lookahead;
    }

    public void start(ScheduledExecutorService executor, Duration pollInterval) {
        long millis = pollInterval.toMillis();
        executor.scheduleWithFixedDelay(this::check, millis, millis, TimeUnit.MILLISECONDS);
    }

    void check() {
        try {
            Instant now = Instant.now();
            for (CalendarEvent event : calendar.listUpcoming(20)) {
                if (event.id() == null) {
                    continue;
                }
                if (shouldNotify(event, now, lookahead) && notified.add(event.id())) {
                    agent.trigger("Przypomnienie z kalendarza: " + event.summary() + ", "
                            + com.jozef.core.util.SpokenDates.range(event.start(), null) + ".");
                }
            }
        } catch (com.jozef.google.auth.GoogleNotSignedInException e) {
            log.debug("Calendar watcher: Google not signed in yet — skipping.");
        } catch (Exception e) {
            log.warn("Calendar check failed: {}", e.getMessage());
        }
    }

    /** True when the event starts within the lookahead window and not in the past. */
    static boolean shouldNotify(CalendarEvent event, Instant now, Duration lookahead) {
        Instant start = parseStart(event.start());
        if (start == null) {
            return false;
        }
        return !start.isBefore(now) && start.isBefore(now.plus(lookahead));
    }

    /** Parses an ISO-8601 date-time (with or without offset) or all-day date. */
    static Instant parseStart(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(iso).toInstant();
        } catch (Exception ignored) {
            // fall through to local date-time
        }
        try {
            return LocalDateTime.parse(iso).atZone(ZoneId.systemDefault()).toInstant();
        } catch (Exception ignored) {
            return null;
        }
    }
}
