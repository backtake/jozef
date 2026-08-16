package com.jozef.planning;

/**
 * Result of executing a plan.
 *
 * @param completed    true when every step succeeded
 * @param failedStep   description of the failing step (null on success)
 * @param error        the error message (null on success)
 */
public record PlanExecutionResult(boolean completed, String failedStep, String error) {

    public static PlanExecutionResult success() {
        return new PlanExecutionResult(true, null, null);
    }

    public static PlanExecutionResult failure(String failedStep, String error) {
        return new PlanExecutionResult(false, failedStep, error);
    }
}
