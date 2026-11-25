package com.jozef.core.agent;

import com.jozef.llm.ToolCall;

import java.util.Optional;

/**
 * A deterministic shortcut tried before the language model. It only <em>resolves</em>
 * an utterance it understands with certainty ("otwórz YouTube") to a tool call; the
 * runtime then executes that call through the normal path (permissions, confirmation,
 * timeout). Anything uncertain returns empty and goes to the model as usual.
 */
public interface FastPath {

    Optional<Resolved> resolve(String utterance);

    /**
     * @param call    the tool to run
     * @param reply   what to say when it succeeded (on failure the model takes over)
     */
    record Resolved(ToolCall call, String reply) {}
}
