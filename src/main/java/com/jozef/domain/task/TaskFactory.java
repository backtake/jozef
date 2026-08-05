package com.jozef.domain.task;

import com.jozef.common.id.IdGenerator;
import com.jozef.common.time.TimeProvider;
import com.jozef.domain.common.TaskDuration;

public class TaskFactory {

    private final IdGenerator idGenerator;
    private final TimeProvider timeProvider;

    public TaskFactory(IdGenerator idGenerator, TimeProvider timeProvider) {
        this.idGenerator = idGenerator;
        this.timeProvider = timeProvider;
    }

    public Task create(String name, TaskDuration estimatedDuration) {
        return new Task(
                idGenerator.generate(),
                name,
                timeProvider.now(),
                estimatedDuration
        );
    }
}
