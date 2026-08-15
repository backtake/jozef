package com.jozef.core.lifecycle;

import com.jozef.core.capabilities.CapabilityLoader;
import com.jozef.core.events.CoreEvents;
import com.jozef.core.events.EventBus;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages the application lifecycle: STARTING → RUNNING → STOPPING → STOPPED.
 */
@Slf4j
public class ApplicationLifecycle {

    private final CapabilityLoader capabilityLoader;
    private final EventBus eventBus;
    private final String version;

    public ApplicationLifecycle(CapabilityLoader capabilityLoader,
                                EventBus eventBus,
                                String version) {
        this.capabilityLoader = capabilityLoader;
        this.eventBus = eventBus;
        this.version = version;
    }

    /**
     * Full startup sequence:
     * 1. Publish ApplicationStarted event
     * 2. Initialize all capabilities in priority order
     */
    public void start() {
        log.info("╔══════════════════════════════════════════╗");
        log.info("║  Jozef AI Agent v{}                  ║", version);
        log.info("║  Event-Driven Personal Assistant        ║");
        log.info("╚══════════════════════════════════════════╝");

        eventBus.publish(new CoreEvents.ApplicationStarted(version));
        capabilityLoader.initializeAll();

        log.info("Jozef is ready.");
    }

    /**
     * Graceful shutdown:
     * 1. Publish ApplicationStopping event
     * 2. Shut down all capabilities in reverse priority order
     */
    public void stop() {
        log.info("Shutting down Jozef...");

        eventBus.publish(new CoreEvents.ApplicationStopping());
        capabilityLoader.shutdownAll();

        log.info("Jozef shut down. Goodbye.");
    }
}
