package com.jozef.proactive;

import com.jozef.configuration.ProactiveProperties;
import com.jozef.core.action.ActionContext;
import com.jozef.core.action.ActionResult;
import com.jozef.core.action.ExecutionStrategy;
import com.jozef.core.action.LambdaAction;
import com.jozef.core.capabilities.Capability;
import com.jozef.core.tool.ToolParameters;
import com.jozef.core.tool.ToolRegistry;
import com.jozef.google.GoogleCapability;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Proactive background agent: timers/reminders plus calendar/gmail watchers.
 *
 * Registers the {@code set_timer}, {@code list_timers} and {@code cancel_timer}
 * tools, and (when Google is configured) starts the calendar + gmail watchers.
 */
@Slf4j
public class ProactiveCapability implements Capability {

    private final ProactiveProperties props;
    private final ToolRegistry toolRegistry;
    private final ProactiveAgentService agent;
    private final GoogleCapability google;

    private ScheduledExecutorService executor;
    private TimerScheduler timerScheduler;

    public ProactiveCapability(ProactiveProperties props,
                               ToolRegistry toolRegistry,
                               ProactiveAgentService agent,
                               GoogleCapability google) {
        this.props = props;
        this.toolRegistry = toolRegistry;
        this.agent = agent;
        this.google = google;
    }

    public TimerScheduler timerScheduler() {
        return timerScheduler;
    }

    @Override
    public String name() {
        return "proactive";
    }

    @Override
    public int priority() {
        return 80;
    }

    @Override
    public void initialize() {
        if (!props.enabled()) {
            log.info("Proactive capability disabled by configuration.");
            return;
        }

        executor = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "jozef-proactive");
            t.setDaemon(true);
            return t;
        });
        timerScheduler = new InMemoryTimerScheduler(executor, message -> agent.trigger("Przypomnienie: " + message));
        registerTools();

        if (google != null) {
            // Also covers Google being connected later (setup finished after startup).
            google.onConnected(this::startGoogleWatchers);
        }

        log.info("Proactive capability initialized.");
    }

    private boolean googleWatchersStarted;

    private synchronized void startGoogleWatchers() {
        if (googleWatchersStarted || executor == null || executor.isShutdown()) return;
        googleWatchersStarted = true;
        if (google.calendar() != null) {
            new CalendarWatcher(google.calendar(), agent, Duration.ofMinutes(props.calendarLookaheadMinutes()))
                    .start(executor, Duration.ofMinutes(props.calendarPollMinutes()));
            log.info("Calendar watcher started (poll={}min, lookahead={}min).",
                    props.calendarPollMinutes(), props.calendarLookaheadMinutes());
        }
        if (google.gmail() != null) {
            new GmailWatcher(google.gmail(), agent, 10)
                    .start(executor, Duration.ofMinutes(props.gmailPollMinutes()));
            log.info("Gmail watcher started (poll={}min).", props.gmailPollMinutes());
        }
    }

    private void registerTools() {
        toolRegistry.register(new LambdaAction(
                "set_timer",
                "Set a reminder that triggers after a delay.",
                new ToolParameters()
                        .string("message", "What to remind about", true)
                        .integer("delay_minutes", "Delay in minutes", false)
                        .integer("delay_seconds", "Delay in seconds", false)
                        .build(),
                Set.of("PROACTIVE"), ExecutionStrategy.SAFE,
                this::setTimer));

        toolRegistry.register(new LambdaAction(
                "list_timers",
                "List active timers/reminders.",
                null,
                Set.of("PROACTIVE"), ExecutionStrategy.SAFE,
                ctx -> {
                    List<ScheduledTimer> active = timerScheduler.active();
                    return ActionResult.ok(formatTimers(active), active);
                }));

        toolRegistry.register(new LambdaAction(
                "cancel_timer",
                "Cancel a timer by id.",
                new ToolParameters().string("timer_id", "ID of the timer to cancel", true).build(),
                Set.of("PROACTIVE"), ExecutionStrategy.SAFE,
                ctx -> {
                    String id = require(ctx, "timer_id");
                    return timerScheduler.cancel(id)
                            ? ActionResult.ok("Timer " + id + " cancelled")
                            : ActionResult.fail("No active timer with id " + id);
                }));
    }

    private ActionResult setTimer(ActionContext ctx) {
        String message = require(ctx, "message");
        long delayMillis = 0;
        if (ctx.get("delay_minutes") instanceof Number n) {
            delayMillis += n.longValue() * 60_000L;
        }
        if (ctx.get("delay_seconds") instanceof Number n) {
            delayMillis += n.longValue() * 1000L;
        }
        if (delayMillis <= 0) {
            return ActionResult.fail("Provide delay_minutes or delay_seconds");
        }
        String id = timerScheduler.schedule(message, Duration.ofMillis(delayMillis));
        return ActionResult.ok("Timer set (" + id + ")");
    }

    private static String formatTimers(List<ScheduledTimer> timers) {
        if (timers.isEmpty()) {
            return "No active timers.";
        }
        StringBuilder sb = new StringBuilder();
        for (ScheduledTimer timer : timers) {
            sb.append("- [").append(timer.id()).append("] ").append(timer.message())
                    .append(" (o ").append(timer.fireAt()).append(")\n");
        }
        return sb.toString().trim();
    }

    private static String require(ActionContext ctx, String key) {
        String value = ctx.getString(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required parameter: " + key);
        }
        return value;
    }

    @Override
    public void shutdown() {
        if (executor != null) {
            executor.shutdownNow();
        }
        log.info("Proactive capability shut down");
    }
}
