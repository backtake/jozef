package com.jozef.core.events;

/**
 * Async event bus — the central nervous system of Jozef.
 * Events are published and all registered listeners execute independently
 * on a thread pool. No listener blocks another.
 */
public interface EventBus {
    /**
     * Register a listener for a specific event type.
     */
    <E extends Event> void register(Class<E> eventType, EventListener<E> listener);

    /**
     * Publish an event. Returns immediately; listeners run asynchronously.
     */
    void publish(Event event);
}
