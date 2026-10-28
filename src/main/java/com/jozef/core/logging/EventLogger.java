package com.jozef.core.logging;

import com.jozef.core.events.Event;
import com.jozef.core.events.EventListener;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Logs every event that flows through the system.
 * Registers itself as a wildcard listener.
 */
@Slf4j
public class EventLogger implements EventListener<Event> {

    private final AtomicInteger eventCount = new AtomicInteger();

    @Override
    public void onEvent(Event event) {
        int n = eventCount.incrementAndGet();
        log.info("[EVENT #{}] {}", n, event.description());
    }

    public int eventCount() {
        return eventCount.get();
    }
}
