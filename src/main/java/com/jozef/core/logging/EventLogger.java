package com.jozef.core.logging;

import com.jozef.core.events.Event;
import com.jozef.core.events.EventListener;
import lombok.extern.slf4j.Slf4j;

/**
 * Logs every event that flows through the system.
 * Registers itself as a wildcard listener.
 */
@Slf4j
public class EventLogger implements EventListener<Event> {

    private int eventCount = 0;

    @Override
    public void onEvent(Event event) {
        eventCount++;
        log.info("[EVENT #{}] {}", eventCount, event.description());
    }

    public int eventCount() {
        return eventCount;
    }
}
