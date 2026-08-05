package com.jozef.domain.plan;

import com.jozef.domain.goal.Goal;

import java.util.ArrayList;

public class PlanFactory {

    public Plan create(Goal goal) {
        return new Plan(goal, new ArrayList<>());
    }
}
