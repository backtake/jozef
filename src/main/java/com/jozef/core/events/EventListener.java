package com.jozef.core.events;

/**
 * Listener for a specific event type. All listeners run asynchronously
 * and independently — they never block each other.
 */
@FunctionalInterface
public interface EventListener<E extends Event> {
    void onEvent(E event);
}
