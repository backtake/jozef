package com.jozef.core.capabilities;

import com.jozef.core.events.EventBus;
import com.jozef.core.events.CoreEvents;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Discovers, sorts, and initializes all Capability beans.
 */
@Slf4j
public class CapabilityLoader {

    private final List<Capability> capabilities;
    private final EventBus eventBus;

    public CapabilityLoader(List<Capability> capabilities, EventBus eventBus) {
        this.capabilities = new ArrayList<>(capabilities);
        this.capabilities.sort(Comparator.comparingInt(Capability::priority));
        this.eventBus = eventBus;
    }

    /**
     * Initialize all capabilities in priority order.
     * If one fails, we log and continue — the system is resilient.
     */
    public void initializeAll() {
        log.info("Initializing {} capabilities...", capabilities.size());

        for (Capability cap : capabilities) {
            try {
                log.info("  ▶ Initializing capability: {} (priority={})", cap.name(), cap.priority());
                cap.initialize();
                eventBus.publish(new CoreEvents.CapabilityInitialized(cap.name()));
                log.info("  ✓ Capability initialized: {}", cap.name());
            } catch (Exception e) {
                log.error("  ✗ Capability {} failed to initialize: {}", cap.name(), e.getMessage(), e);
                eventBus.publish(new CoreEvents.CapabilityFailed(cap.name(), e.getMessage()));
            }
        }

        log.info("All capabilities processed.");
    }

    /**
     * Shutdown all capabilities in reverse priority order.
     */
    public void shutdownAll() {
        log.info("Shutting down {} capabilities...", capabilities.size());

        var reversed = new ArrayList<>(capabilities);
        java.util.Collections.reverse(reversed);

        for (Capability cap : reversed) {
            try {
                log.info("  ◀ Shutting down capability: {}", cap.name());
                cap.shutdown();
                eventBus.publish(new CoreEvents.CapabilityShutdown(cap.name()));
            } catch (Exception e) {
                log.error("  ✗ Capability {} failed to shutdown: {}", cap.name(), e.getMessage(), e);
            }
        }

        log.info("All capabilities shut down.");
    }
}
