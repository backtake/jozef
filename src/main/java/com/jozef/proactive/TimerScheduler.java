package com.jozef.proactive;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Schedules one-shot reminders/timers that trigger the agent later.
 */
public interface TimerScheduler {

    /** Schedule a reminder after a delay. Returns the timer id. */
    String schedule(String message, Duration delay);

    /** Schedule a reminder at an absolute time. Returns the timer id. */
    String scheduleAt(String message, Instant when);

    /** Cancel a timer by id. Returns true if a timer was cancelled. */
    boolean cancel(String id);

    /** Currently pending timers, ordered by fire time. */
    List<ScheduledTimer> active();
}
