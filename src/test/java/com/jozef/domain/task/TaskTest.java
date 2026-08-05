package com.jozef.domain.task;

import com.jozef.domain.common.TaskDuration;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class TaskTest {

    private static final UUID ID = UUID.randomUUID();
    private static final Instant NOW = Instant.now();
    private static final TaskDuration DURATION = new TaskDuration(Duration.ofMinutes(30));

    private Task newTask() {
        return new Task(ID, "Test task", NOW, DURATION, TaskStatus.TODO);
    }

    @Test
    void shouldStartTask() {
        Task task = newTask();

        task.start();

        assertThat(task.status()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void shouldCompleteTask() {
        Task task = newTask();
        task.start();

        task.complete();

        assertThat(task.status()).isEqualTo(TaskStatus.DONE);
    }

    @Test
    void shouldBeDoneAfterComplete() {
        Task task = newTask();
        task.start();
        task.complete();

        assertThat(task.isDone()).isTrue();
    }

    @Test
    void shouldNotBeDoneInitially() {
        Task task = newTask();

        assertThat(task.isDone()).isFalse();
    }

    @Test
    void shouldNotBeDoneWhenInProgress() {
        Task task = newTask();
        task.start();

        assertThat(task.isDone()).isFalse();
    }

    @Test
    void shouldThrowWhenCompletingWithoutStart() {
        Task task = newTask();

        assertThatIllegalStateException()
                .isThrownBy(task::complete)
                .withMessageContaining("Only IN_PROGRESS tasks can be completed");
    }

    @Test
    void shouldThrowWhenStartingAlreadyInProgress() {
        Task task = newTask();
        task.start();

        assertThatIllegalStateException()
                .isThrownBy(task::start)
                .withMessageContaining("Only TODO tasks can be started");
    }

    @Test
    void shouldThrowWhenStartingDoneTask() {
        Task task = newTask();
        task.start();
        task.complete();

        assertThatIllegalStateException()
                .isThrownBy(task::start)
                .withMessageContaining("Only TODO tasks can be started");
    }

    @Test
    void shouldThrowWhenCompletingAlreadyDone() {
        Task task = newTask();
        task.start();
        task.complete();

        assertThatIllegalStateException()
                .isThrownBy(task::complete)
                .withMessageContaining("Only IN_PROGRESS tasks can be completed");
    }
}
