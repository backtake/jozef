package com.jozef.configuration;

/**
 * Agent loop configuration.
 */
public record AgentProperties(
        int maxToolCallsPerTurn,
        int toolTimeoutSeconds
) {}
