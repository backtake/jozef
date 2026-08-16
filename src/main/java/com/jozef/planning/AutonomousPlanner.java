package com.jozef.planning;

import lombok.extern.slf4j.Slf4j;

/**
 * The faza-19 loop: plan → execute → observe → correct.
 *
 * Runs the plan; if a step fails, feeds the failure back to the LLM and
 * re-plans, up to {@code maxAttempts} times.
 */
@Slf4j
public class AutonomousPlanner {

    private final Planner planner;
    private final PlanExecutor executor;
    private final int maxAttempts;

    public AutonomousPlanner(Planner planner, PlanExecutor executor, int maxAttempts) {
        this.planner = planner;
        this.executor = executor;
        this.maxAttempts = maxAttempts > 0 ? maxAttempts : 1;
    }

    public AutonomousResult run(String goal) {
        String currentGoal = goal;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            log.info("Planning attempt {}/{} for: {}", attempt, maxAttempts, goal);
            Plan plan = planner.plan(currentGoal);
            if (plan.getSteps().isEmpty()) {
                return AutonomousResult.failed("The planner produced no steps");
            }

            PlanExecutionResult result = executor.execute(plan);
            if (result.completed()) {
                return AutonomousResult.completed(plan, attempt);
            }

            log.warn("Attempt {} failed at '{}': {}", attempt, result.failedStep(), result.error());
            currentGoal = goal
                    + "\nPoprzednia próba nie powiodła się na kroku: " + result.failedStep()
                    + " (błąd: " + result.error() + "). Zaplanuj ponownie, uwzględniając ten błąd.";
        }
        return AutonomousResult.failed("Nie udało się wykonać po " + maxAttempts + " próbach");
    }
}
