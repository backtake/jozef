package com.jozef.core.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DatesTest {

    @Test
    void resolvesTodayKeywords() {
        String today = LocalDate.now().toString();
        assertThat(Dates.resolveDay("today")).isEqualTo(today);
        assertThat(Dates.resolveDay("dzis")).isEqualTo(today);
        assertThat(Dates.resolveDay("dziś")).isEqualTo(today);
    }

    @Test
    void resolvesTomorrowAndYesterday() {
        assertThat(Dates.resolveDay("tomorrow")).isEqualTo(LocalDate.now().plusDays(1).toString());
        assertThat(Dates.resolveDay("jutro")).isEqualTo(LocalDate.now().plusDays(1).toString());
        assertThat(Dates.resolveDay("yesterday")).isEqualTo(LocalDate.now().minusDays(1).toString());
        assertThat(Dates.resolveDay("wczoraj")).isEqualTo(LocalDate.now().minusDays(1).toString());
    }

    @Test
    void passesThroughIsoDates() {
        assertThat(Dates.resolveDay("2026-12-24")).isEqualTo("2026-12-24");
    }

    @Test
    void returnsNullForUnknownInput() {
        assertThat(Dates.resolveDay(null)).isNull();
        assertThat(Dates.resolveDay("")).isNull();
        assertThat(Dates.resolveDay("  ")).isNull();
        assertThat(Dates.resolveDay("kiedyś")).isNull();
        assertThat(Dates.resolveDay("24/12/2026")).isNull();
    }

    @Test
    void todayReturnsIsoDate() {
        assertThat(Dates.today()).isEqualTo(LocalDate.now().toString());
    }
}
