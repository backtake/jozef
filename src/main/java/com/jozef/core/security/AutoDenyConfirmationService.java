package com.jozef.core.security;

import com.jozef.core.action.ExecutionStrategy;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Fail-safe default: SAFE actions run automatically, while CONFIRM/DANGEROUS
 * actions are denied until a real interactive confirmation service is wired in.
 *
 * This guarantees the LLM can never perform a destructive action by default.
 */
@Slf4j
public class AutoDenyConfirmationService implements ConfirmationService {

    @Override
    public boolean requiresConfirmation(ExecutionStrategy strategy) {
        return strategy != ExecutionStrategy.SAFE;
    }

    @Override
    public boolean confirm(String toolName, Map<String, Object> arguments, ExecutionStrategy strategy) {
        if (strategy == ExecutionStrategy.SAFE) {
            return true;
        }
        log.warn("Tool '{}' requires confirmation ({}) but no interactive confirmation service is configured — denying.",
                toolName, strategy);
        return false;
    }
}
