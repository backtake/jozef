package com.jozef.domain.goal;

import com.jozef.common.event.Event;

public record GoalCreatedEvent(Goal goal) implements Event {
}
