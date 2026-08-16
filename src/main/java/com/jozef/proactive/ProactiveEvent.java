package com.jozef.proactive;

import com.jozef.core.events.Event;

/**
 * Published when a background watcher/timer proactively triggers the agent.
 */
public record ProactiveEvent(String message) implements Event {
    @Override
    public String description() {
        return "ProactiveEvent(" + message + ")";
    }
}
