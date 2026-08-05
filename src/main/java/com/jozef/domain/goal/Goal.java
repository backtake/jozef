package com.jozef.domain.goal;

import java.time.Instant;
import java.util.UUID;

public class Goal {

    private final UUID id;
    private final String name;
    private final Instant createdAt;

    Goal(UUID id, String name, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
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
}
