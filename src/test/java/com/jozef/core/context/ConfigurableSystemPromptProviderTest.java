package com.jozef.core.context;

import com.jozef.configuration.PersonaProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurableSystemPromptProviderTest {

    @Test
    void promptCarriesCurrentDateWeekdayAndZone() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-26T08:30:00Z"), ZoneId.of("Europe/Warsaw"));
        String prompt = new ConfigurableSystemPromptProvider(new PersonaProperties("Jozef", "pl", "concise"), clock)
                .systemPrompt();

        assertThat(prompt)
                .contains("2026-11-26 09:30")
                .contains("czwartek")
                .contains("Thursday")
                .contains("Europe/Warsaw")
                .contains("2026-11-27T14:00")
                .contains("Respond in pl");
    }
}
