package com.jozef.core.security;

import com.jozef.core.action.ExecutionStrategy;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Confirmation through the conversation itself — the default for a background
 * assistant that has no console.
 *
 * The agent runtime recognises this service and, instead of blocking, parks the
 * risky tool call and lets the model ask the user ("Mam wysłać tego maila?").
 * Only the user's next utterance — classified deterministically by
 * {@link ConfirmationReply}, never by the model — can approve it.
 *
 * Callers that cannot hold a conversation (e.g. autonomous plan execution)
 * get a fail-safe {@code false} from {@link #confirm}.
 */
@Slf4j
public class ConversationalConfirmationService implements ConfirmationService {

    @Override
    public boolean requiresConfirmation(ExecutionStrategy strategy) {
        return strategy != ExecutionStrategy.SAFE;
    }

    @Override
    public boolean confirm(String toolName, Map<String, Object> arguments, ExecutionStrategy strategy) {
        if (strategy == ExecutionStrategy.SAFE) {
            return true;
        }
        log.info("Tool '{}' ({}) needs the user's confirmation in conversation — not executing outside a conversation.",
                toolName, strategy);
        return false;
    }
}
