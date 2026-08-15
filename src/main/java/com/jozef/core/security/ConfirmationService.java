package com.jozef.core.security;

import com.jozef.core.action.ExecutionStrategy;

import java.util.Map;

/**
 * Decides whether a tool execution requires interactive confirmation.
 *
 * This is what prevents the LLM from having unlimited authority:
 *   - SAFE      → runs automatically
 *   - CONFIRM   → requires confirmation (send email, delete calendar event)
 *   - DANGEROUS → requires confirmation and is always logged
 */
public interface ConfirmationService {

    /** Whether the given strategy requires user confirmation. */
    boolean requiresConfirmation(ExecutionStrategy strategy);

    /**
     * Ask for (or look up) confirmation for a specific tool invocation.
     * Returns true when the action may proceed.
     */
    boolean confirm(String toolName, Map<String, Object> arguments, ExecutionStrategy strategy);
}
