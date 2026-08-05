package com.jozef.domain.schedule;

import com.jozef.common.time.TimeProvider;
import com.jozef.domain.common.TaskDuration;
import com.jozef.domain.goal.Goal;
import com.jozef.domain.goal.GoalFactory;
import com.jozef.domain.plan.Plan;
import com.jozef.domain.plan.PlanFactory;
import com.jozef.domain.task.Task;
import com.jozef.domain.task.TaskFactory;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleFactoryTest {

    private static final Instant FAKE_NOW = Instant.parse("2025-12-01T15:42:00Z");
    private static final UUID FIXED_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    private final TimeProvider timeProvider = () -> FAKE_NOW;

    private final GoalFactory goalFactory = new GoalFactory(() -> FIXED_ID, timeProvider);
    private final TaskFactory taskFactory = new TaskFactory(() -> FIXED_ID, timeProvider);
    private final PlanFactory planFactory = new PlanFactory();
    private final ScheduleFactory scheduleFactory = new ScheduleFactory(timeProvider);

    @Test
    void shouldCreateScheduleWithCorrectStartedAt() {
        Goal goal = goalFactory.create("Go kayaking");
        Plan plan = planFactory.create(goal);

        Schedule schedule = scheduleFactory.create(plan);

        assertThat(schedule.startedAt()).isEqualTo(FAKE_NOW);
    }

    @Test
    void shouldCalculateEstimatedFinishAtFromRemainingDuration() {
        Goal goal = goalFactory.create("Cook dinner");
        Plan plan = planFactory.create(goal);
        plan.addTask(taskFactory.create("Heat dinner", new TaskDuration(Duration.ofMinutes(10))));
        plan.addTask(taskFactory.create("Eat dinner", new TaskDuration(Duration.ofMinutes(20))));
        plan.addTask(taskFactory.create("Wash dishes", new TaskDuration(Duration.ofMinutes(15))));

        Schedule schedule = scheduleFactory.create(plan);

        assertThat(schedule.estimatedFinishAt()).isEqualTo(Instant.parse("2025-12-01T16:27:00Z"));
    }

    @Test
    void shouldReturnZeroDurationForEmptyPlan() {
        Goal goal = goalFactory.create("Nothing to do");
        Plan plan = planFactory.create(goal);

        Schedule schedule = scheduleFactory.create(plan);

        assertThat(schedule.estimatedFinishAt()).isEqualTo(FAKE_NOW);
    }

    @Test
    void shouldCalculateRemainingTime() {
        Goal goal = goalFactory.create("Partial plan");
        Plan plan = planFactory.create(goal);
        plan.addTask(taskFactory.create("Quick task", new TaskDuration(Duration.ofMinutes(30))));

        Schedule schedule = scheduleFactory.create(plan);

        // 10 minutes have passed
        TimeProvider tenMinutesLater = () -> Instant.parse("2025-12-01T15:52:00Z");
        Duration remaining = schedule.remaining(tenMinutesLater);

        assertThat(remaining).isEqualTo(Duration.ofMinutes(20));
    }

    @Test
    void shouldReturnZeroRemainingWhenPastEstimatedFinish() {
        Goal goal = goalFactory.create("Overdue task");
        Plan plan = planFactory.create(goal);
        plan.addTask(taskFactory.create("Quick task", new TaskDuration(Duration.ofMinutes(10))));

        Schedule schedule = scheduleFactory.create(plan);

        // 30 minutes later — past the estimated finish
        TimeProvider wayLater = () -> Instant.parse("2025-12-01T16:12:00Z");
        Duration remaining = schedule.remaining(wayLater);

        assertThat(remaining).isEqualTo(Duration.ZERO);
    }

    @Test
    void shouldAccountForPartiallyCompletedPlan() {
        Goal goal = goalFactory.create("Mixed progress");
        Plan plan = planFactory.create(goal);
        Task done = taskFactory.create("Already done", new TaskDuration(Duration.ofMinutes(30)));
        Task todo = taskFactory.create("Still to do", new TaskDuration(Duration.ofMinutes(45)));
        plan.addTask(done);
        plan.addTask(todo);

        done.start();
        done.complete();

        Schedule schedule = scheduleFactory.create(plan);

        // Only the 45 min TODO task remains
        assertThat(schedule.estimatedFinishAt()).isEqualTo(Instant.parse("2025-12-01T16:27:00Z"));
    }
}
