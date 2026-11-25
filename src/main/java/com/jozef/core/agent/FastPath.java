package com.jozef.core.agent;

import java.util.Optional;

/**
 * A deterministic handler tried before the language model. It must only claim
 * utterances it understands with certainty ("otwórz YouTube") — anything else
 * returns empty and goes to the model as usual.
 */
public interface FastPath {

    Optional<Handled> tryHandle(String utterance);

    /** The reply to give and the action that was performed. */
    record Handled(String reply, ToolExecution execution) {}
}
