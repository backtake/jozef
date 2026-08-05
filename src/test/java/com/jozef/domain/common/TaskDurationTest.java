package com.jozef.domain.common;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class TaskDurationTest {

    @Test
    void shouldWrapDuration() {
        Duration d = Duration.ofMinutes(30);
        TaskDuration taskDuration = new TaskDuration(d);

        assertThat(taskDuration.value()).isEqualTo(d);
    }

    @Test
    void shouldSupportHoursAndMinutes() {
        TaskDuration taskDuration = new TaskDuration(Duration.ofHours(2).plusMinutes(45));

        assertThat(taskDuration.value()).isEqualTo(Duration.ofMinutes(165));
    }
}
