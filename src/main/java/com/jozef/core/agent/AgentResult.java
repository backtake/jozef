package com.jozef.core.agent;

import java.util.List;

/**
 * The result of one agent turn: the final assistant text plus a record of
 * every tool that was executed along the way.
 */
public record AgentResult(
        String text,
        List<ToolExecution> toolExecutions,
        boolean success
) {
    public static AgentResult reply(String text) {
        return new AgentResult(text, List.of(), true);
    }

    public boolean hasToolExecutions() {
        return toolExecutions != null && !toolExecutions.isEmpty();
    }
}
