package com.jozef.planning;

import com.jozef.core.builtin.GetTimeAction;
import com.jozef.core.tool.ToolRegistry;
import com.jozef.llm.ChatRequest;
import com.jozef.llm.ChatResponse;
import com.jozef.llm.LLMProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlannerTest {

    private static LLMProvider fakeLlm() {
        return new LLMProvider() {
            @Override
            public String name() {
                return "fake";
            }

            @Override
            public ChatResponse chat(ChatRequest request) {
                return new ChatResponse("", List.of());
            }
        };
    }

    private static ToolRegistry toolsWithBuiltins() {
        ToolRegistry tools = new ToolRegistry();
        tools.register(new GetTimeAction());
        return tools;
    }

    @Test
    void extractsJsonArrayFromFencedResponse() {
        assertThat(Planner.extractJsonArray("```json\n[{\"tool\":\"get_current_time\",\"arguments\":{}}]\n```"))
                .isEqualTo("[{\"tool\":\"get_current_time\",\"arguments\":{}}]");
        assertThat(Planner.extractJsonArray("no json here")).isEmpty();
        assertThat(Planner.extractJsonArray(null)).isEmpty();
    }

    @Test
    void parsesStepsAndSkipsUnknownTools() {
        Planner planner = new Planner(fakeLlm(), toolsWithBuiltins());
        String json = "["
                + "{\"tool\":\"get_current_time\",\"arguments\":{},\"description\":\"pobierz czas\"},"
                + "{\"tool\":\"nonexistent\",\"arguments\":{},\"description\":\"zły krok\"}"
                + "]";

        List<PlanStep> steps = planner.parseSteps(json);

        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).getTool()).isEqualTo("get_current_time");
        assertThat(steps.get(0).getDescription()).isEqualTo("pobierz czas");
    }

    @Test
    void systemPromptListsToolsButExcludesMetaTools() {
        Planner planner = new Planner(fakeLlm(), toolsWithBuiltins());

        String prompt = planner.buildSystemPrompt();

        assertThat(prompt).contains("get_current_time");
        assertThat(prompt).doesNotContain("plan_task");
        assertThat(prompt).doesNotContain("autonomous_task");
    }
}
