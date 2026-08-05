package com.jozef.domain.goal;

import com.jozef.common.id.IdGenerator;
import com.jozef.common.time.TimeProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GoalFactoryTest {

    private static final UUID FAKE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant FAKE_NOW = Instant.parse("2025-01-01T12:00:00Z");

    private final IdGenerator idGenerator = () -> FAKE_ID;
    private final TimeProvider timeProvider = () -> FAKE_NOW;
    private final GoalFactory factory = new GoalFactory(idGenerator, timeProvider);

    @Test
    void shouldCreateGoalWithCorrectProperties() {
        Goal goal = factory.create("Learn Rust");

        assertThat(goal.id()).isEqualTo(FAKE_ID);
        assertThat(goal.name()).isEqualTo("Learn Rust");
        assertThat(goal.createdAt()).isEqualTo(FAKE_NOW);
    }

    @Test
    void shouldAssignNonEmptyId() {
        Goal goal = factory.create("Write tests");

        assertThat(goal.id()).isNotNull();
    }
}
