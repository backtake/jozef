package com.jozef.planning;

import com.jozef.configuration.PlanningProperties;
import com.jozef.core.action.ActionContext;
import com.jozef.core.action.ActionResult;
import com.jozef.core.action.ExecutionStrategy;
import com.jozef.core.action.LambdaAction;
import com.jozef.core.capabilities.Capability;
import com.jozef.core.security.ConfirmationService;
import com.jozef.core.security.PermissionManager;
import com.jozef.core.tool.ToolParameters;
import com.jozef.core.tool.ToolRegistry;
import com.jozef.llm.LLMProvider;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;

/**
 * Planning capability: exposes {@code plan_task}, {@code list_plans},
 * {@code execute_plan} and {@code autonomous_task} (plan → execute → observe →
 * correct) tools.
 */
@Slf4j
public class PlanningCapability implements Capability {

    private final PlanningProperties props;
    private final ToolRegistry toolRegistry;
    private final Planner planner;
    private final PlanExecutor executor;
    private final AutonomousPlanner autonomous;
    private final PlanStore store;

    public PlanningCapability(PlanningProperties props,
                              LLMProvider llm,
                              ToolRegistry toolRegistry,
                              PermissionManager permissions,
                              ConfirmationService confirmation,
                              PlanStore store) {
        this.props = props;
        this.toolRegistry = toolRegistry;
        this.planner = new Planner(llm, toolRegistry);
        this.executor = new PlanExecutor(toolRegistry, permissions, confirmation);
        this.autonomous = new AutonomousPlanner(planner, executor, props.maxAttempts());
        this.store = store;
    }

    public PlanStore store() {
        return store;
    }

    @Override
    public String name() {
        return "planning";
    }

    @Override
    public int priority() {
        return 90;
    }

    @Override
    public void initialize() {
        if (!props.enabled()) {
            log.info("Planning capability disabled by configuration.");
            return;
        }
        registerTools();
        log.info("Planning capability initialized (maxAttempts={}).", props.maxAttempts());
    }

    private void registerTools() {
        toolRegistry.register(new LambdaAction(
                "plan_task",
                "Create a step-by-step plan for a goal using the available tools.",
                new ToolParameters().string("goal", "The goal to plan for", true).build(),
                Set.of("PLANNING"), ExecutionStrategy.SAFE,
                ctx -> {
                    Plan plan = planner.plan(require(ctx, "goal"));
                    store.put(plan);
                    return ActionResult.ok(formatPlan(plan), plan.getId());
                }));

        toolRegistry.register(new LambdaAction(
                "list_plans",
                "List created plans.",
                null,
                Set.of("PLANNING"), ExecutionStrategy.SAFE,
                ctx -> ActionResult.ok(formatPlans(store.all()), store.all())));

        toolRegistry.register(new LambdaAction(
                "execute_plan",
                "Execute a previously created plan by id.",
                new ToolParameters().string("plan_id", "ID of the plan to execute", true).build(),
                Set.of("PLANNING"), ExecutionStrategy.CONFIRM,
                ctx -> {
                    Plan plan = store.get(require(ctx, "plan_id"))
                            .orElseThrow(() -> new IllegalArgumentException("Unknown plan id"));
                    PlanExecutionResult result = executor.execute(plan);
                    return result.completed()
                            ? ActionResult.ok("Plan completed: " + plan.getGoal())
                            : ActionResult.fail("Plan failed at " + result.failedStep() + ": " + result.error());
                }));

        toolRegistry.register(new LambdaAction(
                "autonomous_task",
                "Plan, execute and self-correct a multi-step goal autonomously.",
                new ToolParameters().string("goal", "The goal to achieve", true).build(),
                Set.of("PLANNING"), ExecutionStrategy.CONFIRM,
                ctx -> {
                    AutonomousResult result = autonomous.run(require(ctx, "goal"));
                    return result.completed()
                            ? ActionResult.ok("Completed in " + result.attempts()
                                    + " attempt(s): " + summarize(result.plan()))
                            : ActionResult.fail(result.error());
                }));
    }

    private static String formatPlan(Plan plan) {
        StringBuilder sb = new StringBuilder("Plan [").append(plan.getId()).append("]: ").append(plan.getGoal());
        if (plan.getSteps().isEmpty()) {
            sb.append("\n- (no steps)");
        }
        for (int i = 0; i < plan.getSteps().size(); i++) {
            PlanStep step = plan.getSteps().get(i);
            sb.append("\n").append(i + 1).append(". ").append(step.getTool());
            if (!step.getDescription().isBlank()) {
                sb.append(" — ").append(step.getDescription());
            }
        }
        return sb.toString();
    }

    private static String formatPlans(List<Plan> plans) {
        if (plans.isEmpty()) {
            return "No plans.";
        }
        StringBuilder sb = new StringBuilder();
        for (Plan plan : plans) {
            sb.append("- [").append(plan.getId()).append("] ").append(plan.getGoal())
                    .append(" (").append(plan.getStatus()).append(")\n");
        }
        return sb.toString().trim();
    }

    private static String summarize(Plan plan) {
        return plan.getSteps().stream()
                .map(s -> s.getTool() + "=" + s.getStatus())
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    private static String require(ActionContext ctx, String key) {
        String value = ctx.getString(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required parameter: " + key);
        }
        return value;
    }

    @Override
    public void shutdown() {
        log.info("Planning capability shut down");
    }
}
