package com.jozef.core.context;

import com.jozef.core.memory.LongTermMemory;
import com.jozef.core.memory.MemoryEntry;
import com.jozef.core.session.Session;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Builds the context block from long-term memory + the current session's
 * recent actions. Kept deliberately small so the prompt stays lean.
 */
@Slf4j
public class DefaultContextAggregator implements ContextAggregator {

    private final LongTermMemory memory;
    private final int maxNotes;

    public DefaultContextAggregator(LongTermMemory memory, int maxNotes) {
        this.memory = memory;
        this.maxNotes = maxNotes > 0 ? maxNotes : 5;
    }

    @Override
    public String aggregate(Session session) {
        StringBuilder sb = new StringBuilder();

        List<MemoryEntry> notes = memory.recent(maxNotes);
        if (!notes.isEmpty()) {
            sb.append("## Long-term memory (notes)\n");
            for (MemoryEntry note : notes) {
                sb.append("- ").append(note.content()).append("\n");
            }
        }

        List<String> actions = session.lastActions();
        if (!actions.isEmpty()) {
            sb.append("## Recent actions\n");
            for (String action : actions) {
                sb.append("- ").append(action).append("\n");
            }
        }

        return sb.toString().trim();
    }
}
