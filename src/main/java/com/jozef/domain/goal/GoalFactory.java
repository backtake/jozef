package com.jozef.domain.goal;

import com.jozef.common.id.IdGenerator;
import com.jozef.common.time.TimeProvider;

public class GoalFactory {

    private final IdGenerator idGenerator;
    private final TimeProvider timeProvider;

    public GoalFactory(IdGenerator idGenerator, TimeProvider timeProvider) {
        this.idGenerator = idGenerator;
        this.timeProvider = timeProvider;
    }

    public Goal create(String name) {
        return new Goal(
                idGenerator.generate(),
                name,
                timeProvider.now()
        );
    }
}
