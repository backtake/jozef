package com.jozef.application.goal;

import com.jozef.common.command.CommandHandler;
import com.jozef.common.event.EventBus;
import com.jozef.domain.goal.Goal;
import com.jozef.domain.goal.GoalCreatedEvent;
import com.jozef.domain.goal.GoalFactory;
import com.jozef.domain.goal.GoalRepository;

public class CreateGoalCommandHandler implements CommandHandler<CreateGoalCommand> {

    private final GoalFactory goalFactory;
    private final GoalRepository goalRepository;
    private final EventBus eventBus;

    public CreateGoalCommandHandler(GoalFactory goalFactory, GoalRepository goalRepository, EventBus eventBus) {
        this.goalFactory = goalFactory;
        this.goalRepository = goalRepository;
        this.eventBus = eventBus;
    }

    @Override
    public void handle(CreateGoalCommand command) {
        Goal goal = goalFactory.create(command.name());
        goalRepository.save(goal);
        eventBus.publish(new GoalCreatedEvent(goal));
    }
}
