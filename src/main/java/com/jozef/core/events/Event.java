package com.jozef.core.events;

/**
 * Marker interface for all events flowing through the system.
 * Every module only publishes events — never calls other modules directly.
 */
public interface Event {
    /** Human-readable description for logging/debugging. */
    default String description() {
        return getClass().getSimpleName();
    }
}
