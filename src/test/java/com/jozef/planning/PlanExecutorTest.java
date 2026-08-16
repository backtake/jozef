package com.jozef.planning;

import com.jozef.core.action.Action;
import com.jozef.core.action.ActionContext;
import com.jozef.core.action.ActionResult;
import com.jozef.core.security.AutoDenyConfirmationService;
import com.jozef.core.security.PermissionManager;
import com.jozef.core.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlanExecutorTest {

    private static Action okAction() {
        return new Action() {
            @Override public String name() { return "ok_tool"; }
            @Override public String description() { return "succeeds"; }
            @Override public ActionResult execute(ActionContext context) { return ActionResult.ok("done"); }
        };
    }

    private static Action failAction() {
        return new Action() {
            @Override public String name() { return "fail_tool"; }
            @Override public String description() { return "fails"; }
            @Override public ActionResult execute(ActionContext context) { return ActionResult.fail("boom"); }
        };
    }

    private static PlanExecutor executorWith(ToolRegistry tools) {
        return new PlanExecutor(tools, new PermissionManager(), new AutoDenyConfirmationService());
    }

    @Test
    void executesAllStepsSuccessfully() {
        ToolRegistry tools = new ToolRegistry();
        tools.register(okAction());
        Plan plan = new Plan("p1", "goal", List.of(
                new PlanStep("s1", "krok 1", "ok_tool", Map.of()),
                new PlanStep("s2", "krok 2", "ok_tool", Map.of())));

        PlanExecutionResult result = executorWith(tools).execute(plan);

        assertThat(result.completed()).isTrue();
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.COMPLETED);
        assertThat(plan.getSteps()).allMatch(s -> s.getStatus() == StepStatus.SUCCESS);
    }

    @Test
    void stopsOnFirstFailure() {
        ToolRegistry tools = new ToolRegistry();
        tools.register(okAction());
        tools.register(failAction());
        Plan plan = new Plan("p1", "goal", List.of(
                new PlanStep("s1", "ok", "ok_tool", Map.of()),
                new PlanStep("s2", "fail", "fail_tool", Map.of()),
                new PlanStep("s3", "never", "ok_tool", Map.of())));

        PlanExecutionResult result = executorWith(tools).execute(plan);

        assertThat(result.completed()).isFalse();
        assertThat(result.error()).isEqualTo("boom");
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.FAILED);
        assertThat(plan.getSteps().get(1).getStatus()).isEqualTo(StepStatus.FAILED);
        assertThat(plan.getSteps().get(2).getStatus()).isEqualTo(StepStatus.PENDING);
    }

    @Test
    void failsWhenPlanIsEmpty() {
        Plan plan = new Plan("p1", "goal", List.of());

        PlanExecutionResult result = executorWith(new ToolRegistry()).execute(plan);

        assertThat(result.completed()).isFalse();
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.FAILED);
    }
}
