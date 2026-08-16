package com.jozef.planning;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * One step of a plan: a concrete tool invocation with arguments.
 */
@Getter
public class PlanStep {

    private final String id;
    private final String description;
    private final String tool;
    private final Map<String, Object> arguments;

    @Setter
    private StepStatus status = StepStatus.PENDING;
    @Setter
    private String result;

    public PlanStep(String id, String description, String tool, Map<String, Object> arguments) {
        this.id = id;
        this.description = description == null ? "" : description;
        this.tool = tool;
        this.arguments = arguments == null ? Map.of() : arguments;
    }
}
