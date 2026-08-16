package com.jozef.configuration;

/**
 * Security configuration.
 *
 * <pre>
 * jozef:
 *   security:
 *     confirmation-provider: console   # console | auto-deny
 * </pre>
 *
 * {@code auto-deny} is the strictest fail-safe (denies every CONFIRM/DANGEROUS
 * action); {@code console} prompts for a y/n answer on standard input.
 */
public record SecurityProperties(
        String confirmationProvider
) {}
