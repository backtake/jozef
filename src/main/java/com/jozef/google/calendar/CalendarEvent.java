package com.jozef.google.calendar;

/**
 * A single Google Calendar event as exposed to the agent.
 */
public record CalendarEvent(
        String id,
        String summary,
        String start,
        String end,
        String description,
        String location,
        String htmlLink
) {}
