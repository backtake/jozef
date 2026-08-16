package com.jozef.planning;

import com.jozef.core.action.Action;
import com.jozef.core.action.ActionResult;
import com.jozef.core.security.ConfirmationService;
import com.jozef.core.security.PermissionManager;
import com.jozef.core.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * Executes a {@link Plan} step by step through the {@link ToolRegistry},
 * applying the same permission + confirmation policy as the normal agent loop.
 */
@Slf4j
public class PlanExecutor {

    private final ToolRegistry tools;
    private final PermissionManager permissions;
    private final ConfirmationService confirmation;

    public PlanExecutor(ToolRegistry tools, PermissionManager permissions, ConfirmationService confirmation) {
        this.tools = tools;
        this.permissions = permissions;
        this.confirmation = confirmation;
    }

    public PlanExecutionResult execute(Plan plan) {
        if (plan.getSteps().isEmpty()) {
            plan.setStatus(PlanStatus.FAILED);
            return PlanExecutionResult.failure("(no steps)", "Plan is empty");
        }

        plan.setStatus(PlanStatus.RUNNING);
        for (PlanStep step : plan.getSteps()) {
            step.setStatus(StepStatus.RUNNING);

            Action action = tools.find(step.getTool()).orElse(null);
            if (action == null) {
                return fail(plan, step, "Unknown tool: " + step.getTool());
            }
            if (!permissions.isAllowed(action)) {
                return fail(plan, step, "Permission denied: " + step.getTool());
            }
            if (confirmation.requiresConfirmation(action.strategy())
                    && !confirmation.confirm(step.getTool(), step.getArguments(), action.strategy())) {
                return fail(plan, step, "Confirmation required: " + step.getTool());
            }

            ActionResult result = tools.execute(step.getTool(), step.getArguments());
            step.setResult(result.message());
            if (!result.success()) {
                return fail(plan, step, result.message());
            }
            step.setStatus(StepStatus.SUCCESS);
            log.info("Plan step succeeded: {} → {}", step.getTool(), result.message());
        }

        plan.setStatus(PlanStatus.COMPLETED);
        return PlanExecutionResult.success();
    }

    private PlanExecutionResult fail(Plan plan, PlanStep step, String error) {
        step.setStatus(StepStatus.FAILED);
        step.setResult(error);
        plan.setStatus(PlanStatus.FAILED);
        log.warn("Plan step failed: {} → {}", step.getTool(), error);
        return PlanExecutionResult.failure(step.getDescription() + " (" + step.getTool() + ")", error);
    }
}
