package com.jozef.domain.schedule;

import com.jozef.common.time.TimeProvider;
import com.jozef.domain.plan.Plan;

import java.time.Instant;

public class ScheduleFactory {

    private final TimeProvider timeProvider;

    public ScheduleFactory(TimeProvider timeProvider) {
        this.timeProvider = timeProvider;
    }

    public Schedule create(Plan plan) {
        Instant now = timeProvider.now();
        Instant finish = now.plus(plan.remainingDuration());
        return new Schedule(now, finish);
    }
}
