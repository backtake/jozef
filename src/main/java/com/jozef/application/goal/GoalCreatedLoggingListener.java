package com.jozef.application.goal;

import com.jozef.common.event.EventListener;
import com.jozef.domain.goal.GoalCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoalCreatedLoggingListener implements EventListener<GoalCreatedEvent> {

    private static final Logger log = LoggerFactory.getLogger(GoalCreatedLoggingListener.class);

    @Override
    public void onEvent(GoalCreatedEvent event) {
        log.info("Goal created: {} - {}", event.goal().id(), event.goal().name());
    }
}
