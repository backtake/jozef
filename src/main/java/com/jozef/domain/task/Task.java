package com.jozef.domain.task;

import com.jozef.domain.common.TaskDuration;

import java.time.Instant;
import java.util.UUID;

public class Task {

    private final UUID id;
    private final String name;
    private final Instant createdAt;
    private final TaskDuration estimatedDuration;

    Task(UUID id, String name, Instant createdAt, TaskDuration estimatedDuration) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
        this.estimatedDuration = estimatedDuration;
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
}
