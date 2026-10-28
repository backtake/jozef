package com.jozef.assistant;

import com.jozef.core.action.ActionContext;
import com.jozef.core.action.ActionResult;
import com.jozef.core.action.ExecutionStrategy;
import com.jozef.core.action.LambdaAction;
import com.jozef.core.capabilities.Capability;
import com.jozef.core.memory.LongTermMemory;
import com.jozef.core.memory.MemoryEntry;
import com.jozef.core.tool.ToolParameters;
import com.jozef.core.tool.ToolRegistry;
import com.jozef.core.util.Dates;
import com.jozef.google.GoogleCapability;
import com.jozef.google.calendar.CalendarEvent;
import com.jozef.proactive.ProactiveCapability;
import com.jozef.proactive.ScheduledTimer;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;

/**
 * Personal-assistant conveniences that span several capabilities.
 *
 * Registers {@code daily_briefing}, which composes a single "what does my day
 * look like" answer from long-term notes, Google Calendar and active timers.
 * Each source is optional — missing integrations are reported gracefully.
 */
@Slf4j
public class AssistantCapability implements Capability {

    private final LongTermMemory memory;
    private final GoogleCapability google;
    private final ProactiveCapability proactive;
    private final ToolRegistry toolRegistry;

    public AssistantCapability(LongTermMemory memory,
                               GoogleCapability google,
                               ProactiveCapability proactive,
                               ToolRegistry toolRegistry) {
        this.memory = memory;
        this.google = google;
        this.proactive = proactive;
        this.toolRegistry = toolRegistry;
    }

    @Override
    public String name() {
        return "assistant";
    }

    @Override
    public int priority() {
        return 85;
    }

    @Override
    public void initialize() {
        toolRegistry.register(new LambdaAction(
                "daily_briefing",
                "Summarize a day: notes from the notebook, calendar events and active timers. "
                        + "Use for 'what do I have today / tomorrow'.",
                new ToolParameters()
                        .string("date", "Day: ISO yyyy-MM-dd, or 'today'/'tomorrow' (default today)", false)
                        .build(),
                Set.of("ASSISTANT"), ExecutionStrategy.SAFE,
                this::dailyBriefing));

        log.info("Assistant capability initialized (daily_briefing).");
    }

    private ActionResult dailyBriefing(ActionContext ctx) {
        String date = Dates.resolveDay(ctx.getString("date"));
        if (date == null) {
            date = Dates.today();
        }

        StringBuilder sb = new StringBuilder("Plan dnia ").append(date).append(":\n");

        sb.append("\nNotatki:\n");
        List<MemoryEntry> notes = memory.forDate(date);
        if (notes.isEmpty()) {
            sb.append("- brak\n");
        } else {
            for (MemoryEntry note : notes) {
                sb.append("- ").append(note.content()).append("\n");
            }
        }

        sb.append("\nKalendarz:\n");
        if (google != null && google.calendar() != null) {
            try {
                List<CalendarEvent> events = google.calendar().listForDay(date);
                if (events.isEmpty()) {
                    sb.append("- brak wydarzeń\n");
                } else {
                    for (CalendarEvent e : events) {
                        sb.append("- ").append(e.summary());
                        if (e.start() != null) {
                            sb.append(" (o ").append(e.start()).append(")");
                        }
                        sb.append("\n");
                    }
                }
            } catch (Exception e) {
                log.warn("Calendar unavailable for briefing: {}", e.getMessage());
                sb.append("- kalendarz niedostępny\n");
            }
        } else {
            sb.append("- nie skonfigurowano\n");
        }

        sb.append("\nPrzypomnienia (timery):\n");
        if (proactive != null && proactive.timerScheduler() != null) {
            List<ScheduledTimer> timers = proactive.timerScheduler().active();
            if (timers.isEmpty()) {
                sb.append("- brak\n");
            } else {
                for (ScheduledTimer t : timers) {
                    sb.append("- ").append(t.message()).append(" (o ").append(t.fireAt()).append(")\n");
                }
            }
        } else {
            sb.append("- brak\n");
        }

        return ActionResult.ok(sb.toString().trim());
    }

    @Override
    public void shutdown() {
        log.info("Assistant capability shut down");
    }
}
