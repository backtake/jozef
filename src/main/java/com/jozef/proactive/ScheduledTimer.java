package com.jozef.proactive;

import java.time.Instant;

/**
 * A pending timer/reminder.
 */
public record ScheduledTimer(String id, String message, Instant fireAt) {}
