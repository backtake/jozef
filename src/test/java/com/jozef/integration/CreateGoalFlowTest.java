package com.jozef.integration;

import com.jozef.application.goal.CreateGoalCommand;
import com.jozef.application.goal.CreateGoalCommandHandler;
import com.jozef.common.command.SimpleCommandBus;
import com.jozef.common.event.EventListener;
import com.jozef.common.event.SimpleEventBus;
import com.jozef.common.id.IdGenerator;
import com.jozef.common.time.TimeProvider;
import com.jozef.domain.goal.Goal;
import com.jozef.domain.goal.GoalCreatedEvent;
import com.jozef.domain.goal.GoalFactory;
import com.jozef.domain.goal.GoalRepository;
import com.jozef.infrastructure.persistence.InMemoryGoalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CreateGoalFlowTest {

    private static final UUID FAKE_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final Instant FAKE_NOW = Instant.parse("2025-12-15T10:00:00Z");

    private GoalRepository goalRepository;
    private SimpleCommandBus commandBus;
    private SimpleEventBus eventBus;
    private Goal capturedGoal;

    @BeforeEach
    void setUp() {
        IdGenerator idGenerator = () -> FAKE_ID;
        TimeProvider timeProvider = () -> FAKE_NOW;

        goalRepository = new InMemoryGoalRepository();
        eventBus = new SimpleEventBus();
        commandBus = new SimpleCommandBus();

        GoalFactory goalFactory = new GoalFactory(idGenerator, timeProvider);
        CreateGoalCommandHandler handler = new CreateGoalCommandHandler(goalFactory, goalRepository, eventBus);

        commandBus.register(CreateGoalCommand.class, handler);

        // Test listener that captures the goal
        eventBus.register(GoalCreatedEvent.class, (EventListener<GoalCreatedEvent>) event -> capturedGoal = event.goal());
    }

    @Test
    void shouldPersistGoalAndPublishEvent() {
        commandBus.dispatch(new CreateGoalCommand("Learn DDD"));

        // Goal persisted in repository
        assertThat(goalRepository.findById(FAKE_ID)).isPresent();
        Goal persisted = goalRepository.findById(FAKE_ID).get();
        assertThat(persisted.name()).isEqualTo("Learn DDD");
        assertThat(persisted.createdAt()).isEqualTo(FAKE_NOW);

        // Event published and listener invoked
        assertThat(capturedGoal).isNotNull();
        assertThat(capturedGoal.id()).isEqualTo(FAKE_ID);
        assertThat(capturedGoal.name()).isEqualTo("Learn DDD");
    }

    @Test
    void shouldFindAllSavedGoals() {
        commandBus.dispatch(new CreateGoalCommand("First goal"));

        assertThat(goalRepository.findAll()).hasSize(1);
    }
}
