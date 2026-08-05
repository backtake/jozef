package com.jozef.domain.plan;

import com.jozef.common.id.IdGenerator;
import com.jozef.common.time.TimeProvider;
import com.jozef.domain.common.TaskDuration;
import com.jozef.domain.goal.Goal;
import com.jozef.domain.goal.GoalFactory;
import com.jozef.domain.task.Task;
import com.jozef.domain.task.TaskFactory;
import com.jozef.domain.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
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
        Task task = taskFactory.create("Deploy to production", new TaskDuration(Duration.ofHours(1)));

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

    @Test
    void shouldCalculateTotalEstimatedDuration() {
        Goal goal = goalFactory.create("Go kayaking");
        Plan plan = planFactory.create(goal);

        plan.addTask(taskFactory.create("Heat dinner", new TaskDuration(Duration.ofMinutes(10))));
        plan.addTask(taskFactory.create("Eat dinner", new TaskDuration(Duration.ofMinutes(20))));
        plan.addTask(taskFactory.create("Pack kayak", new TaskDuration(Duration.ofMinutes(25))));

        assertThat(plan.estimatedDuration()).isEqualTo(Duration.ofMinutes(55));
    }

    @Test
    void shouldReturnZeroForEmptyPlan() {
        Goal goal = goalFactory.create("Empty goal");
        Plan plan = planFactory.create(goal);

        assertThat(plan.estimatedDuration()).isEqualTo(Duration.ZERO);
    }

    @Test
    void shouldStartFirstTodoTask() {
        Goal goal = goalFactory.create("Ship MVP");
        Plan plan = planFactory.create(goal);
        Task first = taskFactory.create("First task", new TaskDuration(Duration.ofMinutes(10)));
        Task second = taskFactory.create("Second task", new TaskDuration(Duration.ofMinutes(20)));
        plan.addTask(first);
        plan.addTask(second);

        plan.startNextTask();

        assertThat(first.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(second.status()).isEqualTo(TaskStatus.TODO);
    }

    @Test
    void shouldStartNextAfterFirstIsDone() {
        Goal goal = goalFactory.create("Sequential work");
        Plan plan = planFactory.create(goal);
        Task first = taskFactory.create("Task 1", new TaskDuration(Duration.ofMinutes(5)));
        Task second = taskFactory.create("Task 2", new TaskDuration(Duration.ofMinutes(5)));
        plan.addTask(first);
        plan.addTask(second);

        plan.startNextTask();
        first.complete();
        plan.startNextTask();

        assertThat(second.status()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void shouldDoNothingWhenAllTasksDone() {
        Goal goal = goalFactory.create("Done goal");
        Plan plan = planFactory.create(goal);
        Task task = taskFactory.create("Done task", new TaskDuration(Duration.ofMinutes(10)));
        plan.addTask(task);
        plan.startNextTask();
        task.complete();

        plan.startNextTask();

        assertThat(task.status()).isEqualTo(TaskStatus.DONE);
    }

    @Test
    void shouldCalculateRemainingDuration() {
        Goal goal = goalFactory.create("Partial progress");
        Plan plan = planFactory.create(goal);
        Task todo = taskFactory.create("TODO task", new TaskDuration(Duration.ofMinutes(15)));
        Task inProgress = taskFactory.create("In progress", new TaskDuration(Duration.ofMinutes(10)));
        Task done = taskFactory.create("Done task", new TaskDuration(Duration.ofMinutes(30)));
        plan.addTask(todo);
        plan.addTask(inProgress);
        plan.addTask(done);

        inProgress.start();
        done.start();
        done.complete();

        assertThat(plan.remainingDuration()).isEqualTo(Duration.ofMinutes(25));
    }

    @Test
    void shouldCalculateCompletedDuration() {
        Goal goal = goalFactory.create("With done tasks");
        Plan plan = planFactory.create(goal);
        Task done1 = taskFactory.create("Done 1", new TaskDuration(Duration.ofMinutes(20)));
        Task done2 = taskFactory.create("Done 2", new TaskDuration(Duration.ofHours(1)));
        Task todo = taskFactory.create("TODO", new TaskDuration(Duration.ofMinutes(5)));
        plan.addTask(done1);
        plan.addTask(done2);
        plan.addTask(todo);

        done1.start();
        done1.complete();
        done2.start();
        done2.complete();

        assertThat(plan.completedDuration()).isEqualTo(Duration.ofMinutes(80));
    }

    @Test
    void shouldReturnProgress() {
        Goal goal = goalFactory.create("Track progress");
        Plan plan = planFactory.create(goal);
        Task done = taskFactory.create("Done", new TaskDuration(Duration.ofMinutes(10)));
        Task inProgress = taskFactory.create("In progress", new TaskDuration(Duration.ofMinutes(10)));
        Task todo = taskFactory.create("TODO", new TaskDuration(Duration.ofMinutes(10)));
        plan.addTask(done);
        plan.addTask(inProgress);
        plan.addTask(todo);

        done.start();
        done.complete();
        inProgress.start();

        assertThat(plan.progress().done()).isEqualTo(1);
        assertThat(plan.progress().total()).isEqualTo(3);
    }

    @Test
    void shouldReportZeroProgressForEmptyPlan() {
        Goal goal = goalFactory.create("Nothing done");
        Plan plan = planFactory.create(goal);

        assertThat(plan.progress().done()).isEqualTo(0);
        assertThat(plan.progress().total()).isEqualTo(0);
    }
}
