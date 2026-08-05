package com.jozef.domain.schedule;

import com.jozef.common.time.TimeProvider;

import java.time.Duration;
import java.time.Instant;

public class Schedule {

    private final Instant startedAt;
    private final Instant estimatedFinishAt;

    Schedule(Instant startedAt, Instant estimatedFinishAt) {
        this.startedAt = startedAt;
        this.estimatedFinishAt = estimatedFinishAt;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant estimatedFinishAt() {
        return estimatedFinishAt;
    }

    public Duration remaining(TimeProvider timeProvider) {
        Instant now = timeProvider.now();
        if (now.isAfter(estimatedFinishAt)) {
            return Duration.ZERO;
        }
        return Duration.between(now, estimatedFinishAt);
    }
}
