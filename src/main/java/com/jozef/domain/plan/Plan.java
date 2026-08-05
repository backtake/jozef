package com.jozef.domain.plan;

import com.jozef.domain.common.Progress;
import com.jozef.domain.goal.Goal;
import com.jozef.domain.task.Task;
import com.jozef.domain.task.TaskStatus;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Plan {

    private final Goal goal;
    private final List<Task> tasks;

    Plan(Goal goal, List<Task> tasks) {
        this.goal = goal;
        this.tasks = new ArrayList<>(tasks);
    }

    public Goal goal() {
        return goal;
    }

    public List<Task> tasks() {
        return Collections.unmodifiableList(tasks);
    }

    public void addTask(Task task) {
        tasks.add(task);
    }

    public Duration estimatedDuration() {
        return tasks.stream()
                .map(task -> task.estimatedDuration().value())
                .reduce(Duration.ZERO, Duration::plus);
    }

    public void startNextTask() {
        tasks.stream()
                .filter(task -> task.status() == TaskStatus.TODO)
                .findFirst()
                .ifPresent(Task::start);
    }

    public Duration remainingDuration() {
        return tasks.stream()
                .filter(task -> task.status() != TaskStatus.DONE)
                .map(task -> task.estimatedDuration().value())
                .reduce(Duration.ZERO, Duration::plus);
    }

    public Duration completedDuration() {
        return tasks.stream()
                .filter(task -> task.status() == TaskStatus.DONE)
                .map(task -> task.estimatedDuration().value())
                .reduce(Duration.ZERO, Duration::plus);
    }

    public Progress progress() {
        long done = tasks.stream().filter(task -> task.status() == TaskStatus.DONE).count();
        return new Progress((int) done, tasks.size());
    }
}
