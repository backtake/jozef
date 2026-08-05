package com.jozef.common.event;

import org.springframework.stereotype.Component;

@Component
public class SimpleEventBus implements EventBus {

    @Override
    public void publish(Event event) {
        // intentionally empty — foundation for future event handling
    }
}
