package com.jozef.proactive;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * In-memory {@link TimerScheduler} backed by a {@link ScheduledExecutorService}.
 * When a timer fires, its message is handed to the callback (the proactive agent).
 */
@Slf4j
public class InMemoryTimerScheduler implements TimerScheduler {

    private final ScheduledExecutorService executor;
    private final Consumer<String> onFire;
    private final Map<String, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();
    private final Map<String, ScheduledTimer> timers = new ConcurrentHashMap<>();

    public InMemoryTimerScheduler(ScheduledExecutorService executor, Consumer<String> onFire) {
        this.executor = executor;
        this.onFire = onFire;
    }

    @Override
    public String schedule(String message, Duration delay) {
        return scheduleAt(message, Instant.now().plus(delay));
    }

    @Override
    public String scheduleAt(String message, Instant when) {
        String id = UUID.randomUUID().toString();
        timers.put(id, new ScheduledTimer(id, message, when));
        long delayMillis = Math.max(0, when.toEpochMilli() - System.currentTimeMillis());
        futures.put(id, executor.schedule(() -> fire(id), delayMillis, TimeUnit.MILLISECONDS));
        log.info("Timer scheduled: {} at {}", message, when);
        return id;
    }

    private void fire(String id) {
        futures.remove(id);
        ScheduledTimer timer = timers.remove(id);
        if (timer == null) {
            return; // already cancelled
        }
        log.info("Timer fired: {}", timer.message());
        onFire.accept(timer.message());
    }

    @Override
    public boolean cancel(String id) {
        ScheduledFuture<?> future = futures.remove(id);
        ScheduledTimer timer = timers.remove(id);
        if (future != null) {
            future.cancel(false);
        }
        return future != null || timer != null;
    }

    @Override
    public List<ScheduledTimer> active() {
        return timers.values().stream()
                .sorted(Comparator.comparing(ScheduledTimer::fireAt))
                .toList();
    }
}
