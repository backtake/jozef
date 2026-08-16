package com.jozef.planning;

/**
 * Result of an autonomous run (plan → execute → observe → correct).
 */
public record AutonomousResult(boolean completed, Plan plan, int attempts, String error) {

    public static AutonomousResult completed(Plan plan, int attempts) {
        return new AutonomousResult(true, plan, attempts, null);
    }

    public static AutonomousResult failed(String error) {
        return new AutonomousResult(false, null, 0, error);
    }
}
