package com.jozef.configuration;

/**
 * Autonomous planning configuration.
 *
 * <pre>
 * jozef:
 *   planning:
 *     enabled: true
 *     max-attempts: 3
 * </pre>
 */
public record PlanningProperties(
        boolean enabled,
        int maxAttempts
) {}
