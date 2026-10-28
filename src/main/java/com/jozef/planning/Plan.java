package com.jozef.planning;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonCreator
    public Plan(@JsonProperty("id") String id,
                @JsonProperty("goal") String goal,
                @JsonProperty("steps") List<PlanStep> steps) {
        this.id = id;
        this.goal = goal;
        this.steps = steps == null ? List.of() : steps;
    }
}
