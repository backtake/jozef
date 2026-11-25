package com.jozef.core.util;

import java.time.LocalDate;

/** Test access to package-private SpokenDates internals. */
public final class SpokenDatesAccess {
    private SpokenDatesAccess() {}

    public static String range(String start, String end, LocalDate today) {
        return SpokenDates.range(start, end, today);
    }
}
