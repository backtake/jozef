package com.jozef.core.events;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;

/**
 * Fully async event bus. Each listener runs on a thread pool.
 * Listeners for a single event execute concurrently — they never block each other.
 */
@Slf4j
public class AsyncEventBus implements EventBus, AutoCloseable {

    private final Map<Class<?>, List<EventListener<?>>> listeners = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    private volatile boolean shuttingDown = false;

    public AsyncEventBus(ExecutorService executor) {
        this.executor = executor;
    }

    @Override
    public <E extends Event> void register(Class<E> eventType, EventListener<E> listener) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(listener);
        log.debug("Registered listener {} for event {}", listener.getClass().getSimpleName(), eventType.getSimpleName());
    }

    @Override
    @SuppressWarnings("unchecked")
    public void publish(Event event) {
        if (shuttingDown) {
            log.trace("Event bus is shutting down, running listeners for {} synchronously", event.description());
            dispatchSynchronously(event, collectListeners(event));
            return;
        }

        List<EventListener<?>> matching = collectListeners(event);

        if (matching.isEmpty()) {
            log.trace("No listeners for event {}", event.description());
            return;
        }

        log.debug("Publishing event {} to {} listener(s)", event.description(), matching.size());

        for (EventListener<?> listener : matching) {
            try {
                executor.submit(() -> invoke(listener, event));
            } catch (java.util.concurrent.RejectedExecutionException e) {
                // Executor already shut down — run listener synchronously
                log.trace("Executor shut down, running listener {} synchronously",
                        listener.getClass().getSimpleName());
                invoke(listener, event);
            }
        }
    }

    /**
     * Collect all listeners matching the event type by walking the class hierarchy
     * and implemented interfaces.
     */
    private List<EventListener<?>> collectListeners(Event event) {
        List<EventListener<?>> matching = new java.util.ArrayList<>();
        Class<?> clazz = event.getClass();
        while (clazz != null && Event.class.isAssignableFrom(clazz)) {
            List<EventListener<?>> forClass = listeners.get(clazz);
            if (forClass != null) matching.addAll(forClass);
            for (Class<?> iface : clazz.getInterfaces()) {
                if (Event.class.isAssignableFrom(iface)) {
                    List<EventListener<?>> forIface = listeners.get(iface);
                    if (forIface != null) matching.addAll(forIface);
                }
            }
            clazz = clazz.getSuperclass();
        }
        return matching;
    }

    private void dispatchSynchronously(Event event, List<EventListener<?>> matching) {
        for (EventListener<?> listener : matching) {
            invoke(listener, event);
        }
    }

    @SuppressWarnings("unchecked")
    private void invoke(EventListener<?> listener, Event event) {
        try {
            ((EventListener<Event>) listener).onEvent(event);
        } catch (Exception e) {
            log.error("Listener {} failed on event {}: {}",
                    listener.getClass().getSimpleName(), event.description(), e.getMessage(), e);
        }
    }

    /**
     * Graceful shutdown: stop accepting new publishes, drain queued listeners,
     * and wait up to a bounded time for in-flight listeners to finish.
     */
    @Override
    public void close() {
        if (shuttingDown) return;
        shuttingDown = true;
        log.info("Shutting down async event bus...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                log.warn("Event bus executor did not terminate within 10s; forcing shutdown.");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        log.info("Async event bus shut down.");
    }
}
