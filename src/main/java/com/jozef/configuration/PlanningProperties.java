package com.jozef.configuration;

/**
 * Autonomous planning configuration.
 *
 * <pre>
 * jozef:
 *   planning:
 *     enabled: true
 *     max-attempts: 3
 *     file: data/plans.json    # persistent plan store
 * </pre>
 */
public record PlanningProperties(
        boolean enabled,
        int maxAttempts,
        String file
) {}
