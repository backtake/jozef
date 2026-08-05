package com.jozef.domain.task;

import com.jozef.domain.common.TaskDuration;

import java.time.Instant;
import java.util.UUID;

public class Task {

    private final UUID id;
    private final String name;
    private final Instant createdAt;
    private final TaskDuration estimatedDuration;
    private TaskStatus status;

    Task(UUID id, String name, Instant createdAt, TaskDuration estimatedDuration, TaskStatus status) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
        this.estimatedDuration = estimatedDuration;
        this.status = status;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public TaskDuration estimatedDuration() {
        return estimatedDuration;
    }

    public TaskStatus status() {
        return status;
    }

    public void start() {
        if (status != TaskStatus.TODO) {
            throw new IllegalStateException("Only TODO tasks can be started. Current status: " + status);
        }
        status = TaskStatus.IN_PROGRESS;
    }

    public void complete() {
        if (status != TaskStatus.IN_PROGRESS) {
            throw new IllegalStateException("Only IN_PROGRESS tasks can be completed. Current status: " + status);
        }
        status = TaskStatus.DONE;
    }

    public boolean isDone() {
        return status == TaskStatus.DONE;
    }
}
