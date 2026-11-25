package com.jozef.core.memory;

import com.jozef.core.action.ActionContext;
import com.jozef.core.action.ActionResult;
import com.jozef.core.action.ExecutionStrategy;
import com.jozef.core.action.LambdaAction;
import com.jozef.core.capabilities.Capability;
import com.jozef.core.tool.ToolParameters;
import com.jozef.core.tool.ToolRegistry;
import com.jozef.core.util.Dates;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Exposes the long-term memory to the agent as tools:
 * {@code remember_note}, {@code recall_notes}, {@code forget_note}.
 *
 * <p>Notes can be attached to a calendar day via the optional {@code date}
 * parameter, which turns the store into a simple notebook for daily
 * arrangements and plans for today/future days.</p>
 */
@Slf4j
public class MemoryCapability implements Capability {

    private final LongTermMemory memory;
    private final ToolRegistry toolRegistry;

    public MemoryCapability(LongTermMemory memory, ToolRegistry toolRegistry) {
        this.memory = memory;
        this.toolRegistry = toolRegistry;
    }

    public LongTermMemory memory() {
        return memory;
    }

    @Override
    public String name() {
        return "memory";
    }

    @Override
    public int priority() {
        return 45;
    }

    @Override
    public void initialize() {
        toolRegistry.register(new LambdaAction(
                "remember_note",
                "Save a fact/note to long-term memory (persists across restarts). "
                        + "Use the optional 'date' to attach it to a specific day for daily plans.",
                new ToolParameters()
                        .string("content", "The note content to remember", true)
                        .string("tags", "Optional comma-separated tags", false)
                        .string("date", "Optional day: ISO yyyy-MM-dd, or 'today'/'tomorrow'. Empty = general note.", false)
                        .build(),
                Set.of("MEMORY"), ExecutionStrategy.SAFE,
                ctx -> {
                    String content = require(ctx, "content");
                    List<String> tags = parseTags(ctx.getString("tags"));
                    String date = resolveDate(ctx.getString("date"));
                    MemoryEntry entry = memory.remember(content, tags, date);
                    return ActionResult.ok("Remembered note " + entry.id() + (date != null ? " for " + date : ""), entry);
                }));

        toolRegistry.register(new LambdaAction(
                "recall_notes",
                "Search/list notes stored in long-term memory. Use 'date' to list notes for a specific day.",
                new ToolParameters()
                        .string("query", "Search query (empty = all notes)", false)
                        .string("date", "Optional day filter: ISO yyyy-MM-dd, or 'today'/'tomorrow'", false)
                        .integer("limit", "Maximum number of notes (default 10)", false)
                        .build(),
                Set.of("MEMORY"), ExecutionStrategy.SAFE,
                ctx -> {
                    int limit = ctx.get("limit") instanceof Number n ? n.intValue() : 10;
                    String query = ctx.getString("query");
                    String dateRaw = ctx.getString("date");
                    List<MemoryEntry> notes;
                    if (dateRaw != null && !dateRaw.isBlank()) {
                        notes = memory.forDate(resolveDate(dateRaw));
                    } else if (query != null && !query.isBlank()) {
                        notes = memory.search(query);
                    } else {
                        notes = memory.recent(limit);
                    }
                    if (notes.size() > limit) {
                        notes = notes.subList(0, limit);
                    }
                    return ActionResult.ok(formatNotes(notes), notes);
                }));

        toolRegistry.register(new LambdaAction(
                "recall_day_notes",
                "List notes attached to a specific day (default: today).",
                new ToolParameters()
                        .string("date", "Day: ISO yyyy-MM-dd, or 'today'/'tomorrow' (default today)", false)
                        .build(),
                Set.of("MEMORY"), ExecutionStrategy.SAFE,
                ctx -> {
                    String date = resolveDate(ctx.getString("date"));
                    if (date == null) {
                        date = LocalDate.now().toString();
                    }
                    List<MemoryEntry> notes = memory.forDate(date);
                    return ActionResult.ok(formatNotes(notes), notes);
                }));

        toolRegistry.register(new LambdaAction(
                "forget_note",
                "Delete a note from long-term memory by id.",
                new ToolParameters().string("note_id", "ID of the note to delete", true).build(),
                Set.of("MEMORY"), ExecutionStrategy.CONFIRM,
                ctx -> {
                    String id = require(ctx, "note_id");
                    boolean removed = memory.forget(id);
                    return removed
                            ? ActionResult.ok("Forgot note " + id)
                            : ActionResult.fail("No note with id " + id);
                }).withConfirmationSummary(args -> {
                    String id = String.valueOf(args.get("note_id"));
                    return memory.all().stream()
                            .filter(n -> n.id().equals(id))
                            .findFirst()
                            .map(n -> "usunąć notatkę: „" + (n.content().length() > 160
                                    ? n.content().substring(0, 157) + "…" : n.content()) + "”")
                            .orElse("usunąć notatkę o identyfikatorze " + id + " (nie ma takiej notatki)");
                }));

        log.info("Memory capability initialized ({} tools registered).", toolRegistry.size());
    }

    private static List<String> parseTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(t -> !t.isBlank())
                .toList();
    }

    /** Maps common day keywords to ISO dates; passes through/returns null otherwise. */
    static String resolveDate(String raw) {
        return Dates.resolveDay(raw);
    }

    private static String formatNotes(List<MemoryEntry> notes) {
        if (notes.isEmpty()) {
            return "No notes.";
        }
        StringBuilder sb = new StringBuilder();
        for (MemoryEntry note : notes) {
            sb.append("- [").append(note.id()).append("] ");
            if (note.date() != null) {
                sb.append("(").append(note.date()).append(") ");
            }
            sb.append(note.content());
            if (!note.tags().isEmpty()) {
                sb.append(" #").append(String.join(" #", note.tags()));
            }
            sb.append("\n");
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
        log.info("Memory capability shut down");
    }
}
