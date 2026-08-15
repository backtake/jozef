package com.jozef.core.capabilities;

/**
 * A Capability is the top-level abstraction for any subsystem.
 * Speech, LLM, Overlay, Automation, Plugins — everything is a Capability.
 *
 * Bootstrap loads all Capabilities. It doesn't care what they do.
 *
 * This inverts the architecture: LLM is just ONE capability, not the center.
 */
public interface Capability {
    /** Unique name, e.g. "speech", "llm", "overlay", "automation" */
    String name();

    /** Initialize the capability. Called at startup. */
    void initialize();

    /** Gracefully shut down the capability. Called at shutdown. */
    void shutdown();

    /** Priority — lower numbers initialize first */
    default int priority() {
        return 100;
    }
}
