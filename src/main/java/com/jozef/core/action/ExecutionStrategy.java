package com.jozef.core.action;

/**
 * How risky an action is, which drives the confirmation policy.
 *
 * <pre>
 *   SAFE      — can run automatically (read calendar, open browser)
 *   CONFIRM   — requires user confirmation (send email, delete calendar event)
 *   DANGEROUS — requires explicit confirmation and is always logged (delete file, shutdown)
 * </pre>
 */
public enum ExecutionStrategy {
    SAFE,
    CONFIRM,
    DANGEROUS
}
