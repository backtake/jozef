package com.jozef.core.session;

import lombok.extern.slf4j.Slf4j;

/**
 * Manages session lifecycle. Creates a new session on startup.
 * In the future, sessions can be persisted and restored.
 */
@Slf4j
public class SessionManager {

    private volatile Session currentSession;

    public SessionManager() {
        this.currentSession = new Session();
        log.info("Session created: {}", currentSession.id());
    }

    public Session current() {
        return currentSession;
    }

    public Session reset() {
        var oldId = currentSession.id();
        this.currentSession = new Session();
        log.info("Session reset: {} → {}", oldId, currentSession.id());
        return currentSession;
    }
}
