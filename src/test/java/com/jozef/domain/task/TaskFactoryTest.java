package com.jozef.domain.task;

import com.jozef.common.id.IdGenerator;
import com.jozef.common.time.TimeProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TaskFactoryTest {

    private static final UUID FAKE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant FAKE_NOW = Instant.parse("2025-06-15T08:00:00Z");

    private final IdGenerator idGenerator = () -> FAKE_ID;
    private final TimeProvider timeProvider = () -> FAKE_NOW;
    private final TaskFactory factory = new TaskFactory(idGenerator, timeProvider);

    @Test
    void shouldCreateTaskWithCorrectProperties() {
        Task task = factory.create("Design the architecture");

        assertThat(task.id()).isEqualTo(FAKE_ID);
        assertThat(task.name()).isEqualTo("Design the architecture");
        assertThat(task.createdAt()).isEqualTo(FAKE_NOW);
    }

    @Test
    void shouldAssignNonEmptyId() {
        Task task = factory.create("Write integration tests");

        assertThat(task.id()).isNotNull();
    }
}
