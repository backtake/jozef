package com.jozef.domain.plan;

import com.jozef.common.id.IdGenerator;
import com.jozef.common.time.TimeProvider;
import com.jozef.domain.goal.Goal;
import com.jozef.domain.goal.GoalFactory;
import com.jozef.domain.task.Task;
import com.jozef.domain.task.TaskFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlanTest {

    private static final UUID GOAL_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID TASK_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final Instant FAKE_NOW = Instant.parse("2025-09-01T10:00:00Z");

    private final IdGenerator idGenerator = () -> TASK_ID;
    private final TimeProvider timeProvider = () -> FAKE_NOW;

    private final GoalFactory goalFactory = new GoalFactory(() -> GOAL_ID, () -> FAKE_NOW);
    private final TaskFactory taskFactory = new TaskFactory(idGenerator, timeProvider);
    private final PlanFactory planFactory = new PlanFactory();

    @Test
    void shouldCreateEmptyPlan() {
        Goal goal = goalFactory.create("Master DDD");
        Plan plan = planFactory.create(goal);

        assertThat(plan.goal()).isEqualTo(goal);
        assertThat(plan.tasks()).isEmpty();
    }

    @Test
    void shouldAddTaskToPlan() {
        Goal goal = goalFactory.create("Ship MVP");
        Plan plan = planFactory.create(goal);
        Task task = taskFactory.create("Deploy to production");

        plan.addTask(task);

        assertThat(plan.tasks()).hasSize(1);
        assertThat(plan.tasks()).containsExactly(task);
    }

    @Test
    void shouldReturnUnmodifiableTaskList() {
        Goal goal = goalFactory.create("Learn Rust");
        Plan plan = planFactory.create(goal);

        assertThat(plan.tasks()).isUnmodifiable();
    }
}
