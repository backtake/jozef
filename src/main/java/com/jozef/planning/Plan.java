package com.jozef.planning;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * A sequence of steps produced by the planner to reach a goal.
 */
@Getter
public class Plan {

    private final String id;
    private final String goal;
    private final List<PlanStep> steps;

    @Setter
    private PlanStatus status = PlanStatus.CREATED;

    public Plan(String id, String goal, List<PlanStep> steps) {
        this.id = id;
        this.goal = goal;
        this.steps = steps == null ? List.of() : steps;
    }
}
