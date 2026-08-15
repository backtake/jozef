package com.jozef.core.health;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically checks the health of registered components.
 * Publishes HealthCheckPassed / HealthCheckFailed events.
 */
@Slf4j
public class HealthMonitor implements AutoCloseable {

    private final Map<String, HealthCheck> checks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final com.jozef.core.events.EventBus eventBus;
    private final int intervalSeconds;

    public HealthMonitor(com.jozef.core.events.EventBus eventBus, int intervalSeconds) {
        this.eventBus = eventBus;
        this.intervalSeconds = intervalSeconds;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "health-monitor");
            t.setDaemon(true);
            return t;
        });
    }

    public void register(String component, HealthCheck check) {
        checks.put(component, check);
        log.info("Health check registered: {}", component);
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::runChecks, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        log.info("Health monitor started (interval={}s)", intervalSeconds);
    }

    private void runChecks() {
        checks.forEach((component, check) -> {
            try {
                if (check.isHealthy()) {
                    log.trace("Health OK: {}", component);
                } else {
                    log.warn("Health FAIL: {}", component);
                    eventBus.publish(new com.jozef.core.events.CoreEvents.HealthCheckFailed(component, "check returned false"));
                }
            } catch (Exception e) {
                log.error("Health check {} threw: {}", component, e.getMessage());
                eventBus.publish(new com.jozef.core.events.CoreEvents.HealthCheckFailed(component, e.getMessage()));
            }
        });
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }

    @FunctionalInterface
    public interface HealthCheck {
        boolean isHealthy();
    }
}
