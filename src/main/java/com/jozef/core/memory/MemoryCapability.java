package com.jozef.core.memory;

import com.jozef.core.action.ActionContext;
import com.jozef.core.action.ActionResult;
import com.jozef.core.action.ExecutionStrategy;
import com.jozef.core.action.LambdaAction;
import com.jozef.core.capabilities.Capability;
import com.jozef.core.tool.ToolParameters;
import com.jozef.core.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Exposes the long-term memory to the agent as tools:
 * {@code remember_note}, {@code recall_notes}, {@code forget_note}.
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
                "Save a fact/note to long-term memory (persists across restarts).",
                new ToolParameters()
                        .string("content", "The note content to remember", true)
                        .string("tags", "Optional comma-separated tags", false)
                        .build(),
                Set.of("MEMORY"), ExecutionStrategy.SAFE,
                ctx -> {
                    String content = require(ctx, "content");
                    List<String> tags = parseTags(ctx.getString("tags"));
                    MemoryEntry entry = memory.remember(content, tags);
                    return ActionResult.ok("Remembered note " + entry.id(), entry);
                }));

        toolRegistry.register(new LambdaAction(
                "recall_notes",
                "Search/list notes stored in long-term memory.",
                new ToolParameters()
                        .string("query", "Search query (empty = all notes)", false)
                        .integer("limit", "Maximum number of notes (default 10)", false)
                        .build(),
                Set.of("MEMORY"), ExecutionStrategy.SAFE,
                ctx -> {
                    int limit = ctx.get("limit") instanceof Number n ? n.intValue() : 10;
                    String query = ctx.getString("query");
                    List<MemoryEntry> notes = (query == null || query.isBlank())
                            ? memory.recent(limit)
                            : memory.search(query);
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

    private static String formatNotes(List<MemoryEntry> notes) {
        if (notes.isEmpty()) {
            return "No notes.";
        }
        StringBuilder sb = new StringBuilder();
        for (MemoryEntry note : notes) {
            sb.append("- [").append(note.id()).append("] ").append(note.content());
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
