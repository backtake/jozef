package com.jozef.core.context;

import com.jozef.core.session.Session;

/**
 * Aggregates everything the LLM should know beyond the immediate message:
 * long-term memory notes, recent actions, active context.
 */
public interface ContextAggregator {

    /**
     * Builds a context block to append to the system prompt.
     * Returns an empty string when there is nothing to add.
     */
    String aggregate(Session session);
}
