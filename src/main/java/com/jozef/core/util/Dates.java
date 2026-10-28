package com.jozef.core.util;

import java.time.LocalDate;

/**
 * Small date helpers shared across capabilities.
 */
public final class Dates {

    private Dates() {
    }

    /**
     * Resolves common day keywords to an ISO date ({@code yyyy-MM-dd}).
     * Returns {@code null} for blank or unrecognized values.
     */
    public static String resolveDay(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.trim().toLowerCase();
        return switch (v) {
            case "today", "dzis", "dziś" -> LocalDate.now().toString();
            case "tomorrow", "jutro" -> LocalDate.now().plusDays(1).toString();
            case "yesterday", "wczoraj" -> LocalDate.now().minusDays(1).toString();
            default -> v.matches("\\d{4}-\\d{2}-\\d{2}") ? v : null;
        };
    }

    /** Today as an ISO date. */
    public static String today() {
        return LocalDate.now().toString();
    }
}
