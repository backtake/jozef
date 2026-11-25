package com.jozef.core.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Dates the way a person says them in Polish: "jutro o 14:00", "w piątek 28.11 o 9:30".
 * Used for confirmation questions, so the user approves what they actually understand.
 */
public final class SpokenDates {

    private static final Locale PL = Locale.forLanguageTag("pl");
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("H:mm");
    private static final DateTimeFormatter DM = DateTimeFormatter.ofPattern("d.MM");

    private SpokenDates() {}

    public static String range(String startIso, String endIso) {
        return range(startIso, endIso, LocalDate.now());
    }

    static String range(String startIso, String endIso, LocalDate today) {
        LocalDateTime start = parse(startIso);
        LocalDateTime end = parse(endIso);
        if (start == null) return startIso == null ? "" : startIso;
        String s = day(start.toLocalDate(), today) + " o " + HM.format(start);
        if (end != null) {
            s += end.toLocalDate().equals(start.toLocalDate())
                    ? " do " + HM.format(end)
                    : " do " + day(end.toLocalDate(), today) + " " + HM.format(end);
        }
        return s;
    }

    private static String day(LocalDate date, LocalDate today) {
        if (date.equals(today)) return "dziś";
        if (date.equals(today.plusDays(1))) return "jutro";
        if (date.equals(today.plusDays(2))) return "pojutrze";
        String weekday = date.getDayOfWeek().getDisplayName(TextStyle.FULL, PL);
        return weekday + " " + DM.format(date);
    }

    private static LocalDateTime parse(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        } catch (Exception ignored) {
            // no offset
        }
        try {
            return LocalDateTime.parse(iso);
        } catch (Exception ignored) {
            // date only
        }
        try {
            return LocalDate.parse(iso).atStartOfDay();
        } catch (Exception e) {
            return null;
        }
    }
}
