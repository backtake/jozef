package com.jozef.planning;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jozef.core.tool.ToolDefinition;
import com.jozef.core.tool.ToolRegistry;
import com.jozef.llm.ChatMessage;
import com.jozef.llm.ChatRequest;
import com.jozef.llm.ChatResponse;
import com.jozef.llm.LLMProvider;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Asks the LLM to decompose a goal into a sequence of tool steps (JSON) and
 * parses them into a {@link Plan}. Tools that manage plans themselves are
 * excluded so the planner cannot recurse.
 */
@Slf4j
public class Planner {

    private static final Set<String> META_TOOLS =
            Set.of("plan_task", "execute_plan", "list_plans", "autonomous_task");

    private final LLMProvider llm;
    private final ToolRegistry tools;
    private final ObjectMapper mapper = new ObjectMapper();

    public Planner(LLMProvider llm, ToolRegistry tools) {
        this.llm = llm;
        this.tools = tools;
    }

    public Plan plan(String goal) {
        String system = buildSystemPrompt();
        ChatResponse response = llm.chat(new ChatRequest(
                system, List.of(ChatMessage.user(goal)), List.of(), 0.2, 2048));
        String json = extractJsonArray(response.content());
        List<PlanStep> steps = parseSteps(json);
        return new Plan(UUID.randomUUID().toString(), goal, steps);
    }

    String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a task planner. Break the user's goal into a sequence of steps.\n");
        sb.append("Each step must use exactly one of the available tools.\n\n");
        sb.append("Available tools:\n");
        for (ToolDefinition def : tools.definitions()) {
            if (META_TOOLS.contains(def.name())) {
                continue;
            }
            sb.append("- ").append(def.name()).append(": ").append(def.description()).append("\n");
        }
        sb.append("\nRespond with ONLY a JSON array (no markdown, no other text). Each element:\n");
        sb.append("{\"tool\": \"<tool name>\", \"arguments\": {..}, \"description\": \"<why this step>\"}\n");
        sb.append("Use empty arguments {} when the tool needs none.");
        return sb.toString();
    }

    /** Extracts the first JSON array from an LLM response, stripping code fences. */
    static String extractJsonArray(String content) {
        if (content == null) {
            return "";
        }
        String s = content.trim();
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            if (firstNewline >= 0) {
                s = s.substring(firstNewline + 1);
            }
            s = s.replace("```", "").trim();
        }
        int start = s.indexOf('[');
        int end = s.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return "";
        }
        return s.substring(start, end + 1);
    }

    List<PlanStep> parseSteps(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> raw = mapper.readValue(json, new TypeReference<>() {});
            List<PlanStep> steps = new ArrayList<>();
            for (Map<String, Object> item : raw) {
                String tool = item.get("tool") == null ? null : String.valueOf(item.get("tool"));
                if (tool == null || !tools.knows(tool)) {
                    log.warn("Plan step uses unknown tool '{}' — skipping.", tool);
                    continue;
                }
                Object args = item.get("arguments");
                Map<String, Object> arguments = args instanceof Map
                        ? (Map<String, Object>) args
                        : Map.of();
                String description = item.get("description") == null
                        ? "" : String.valueOf(item.get("description"));
                steps.add(new PlanStep(UUID.randomUUID().toString(), description, tool, arguments));
            }
            return steps;
        } catch (Exception e) {
            log.warn("Could not parse plan JSON: {}", e.getMessage());
            return List.of();
        }
    }
}
