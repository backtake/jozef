package com.jozef.core.agent;

import com.jozef.configuration.AgentProperties;
import com.jozef.configuration.LlmProperties;
import com.jozef.configuration.MemoryProperties;
import com.jozef.core.action.Action;
import com.jozef.core.action.ActionResult;
import com.jozef.core.context.ContextAggregator;
import com.jozef.core.context.SystemPromptProvider;
import com.jozef.core.events.AgentEvents;
import com.jozef.core.events.EventBus;
import com.jozef.core.security.ConfirmationService;
import com.jozef.core.security.PermissionManager;
import com.jozef.core.session.SessionManager;
import com.jozef.core.tool.ToolDefinition;
import com.jozef.core.tool.ToolRegistry;
import com.jozef.llm.ChatMessage;
import com.jozef.llm.ChatRequest;
import com.jozef.llm.ChatResponse;
import com.jozef.llm.LLMProvider;
import com.jozef.llm.ToolCall;
import com.jozef.llm.ToolSchema;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The agent reasoning loop — the heart of Jozef.
 *
 * <pre>
 *   user message → LLM → normal response? → speak
 *                      └→ tool calls → validate → execute → feed results back → LLM → final response
 * </pre>
 *
 * The Agent is the ONLY orchestrator. Capabilities (browser, gmail, windows, …)
 * are reached exclusively through the {@link ToolRegistry}; the LLM never
 * touches them directly.
 */
@Slf4j
public class AgentRuntime {

    private final LLMProvider llm;
    private final ToolRegistry toolRegistry;
    private final SystemPromptProvider systemPromptProvider;
    private final EventBus eventBus;
    private final SessionManager sessions;
    private final PermissionManager permissionManager;
    private final ConfirmationService confirmationService;
    private final AgentProperties agentProperties;
    private final LlmProperties llmProperties;
    private final ContextAggregator contextAggregator;
    private final MemoryProperties memoryProperties;

    public AgentRuntime(LLMProvider llm,
                        ToolRegistry toolRegistry,
                        SystemPromptProvider systemPromptProvider,
                        EventBus eventBus,
                        SessionManager sessions,
                        PermissionManager permissionManager,
                        ConfirmationService confirmationService,
                        AgentProperties agentProperties,
                        LlmProperties llmProperties,
                        ContextAggregator contextAggregator,
                        MemoryProperties memoryProperties) {
        this.llm = llm;
        this.toolRegistry = toolRegistry;
        this.systemPromptProvider = systemPromptProvider;
        this.eventBus = eventBus;
        this.sessions = sessions;
        this.permissionManager = permissionManager;
        this.confirmationService = confirmationService;
        this.agentProperties = agentProperties;
        this.llmProperties = llmProperties;
        this.contextAggregator = contextAggregator;
        this.memoryProperties = memoryProperties;
    }

    /**
     * Process one user utterance and return the final response text.
     * Synchronized so voice and proactive triggers never interleave a turn.
     */
    public synchronized AgentResult process(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return AgentResult.reply("");
        }

        eventBus.publish(new AgentEvents.UserCommandReceived(userMessage));
        sessions.current().addTurn("user", userMessage);

        List<ChatMessage> messages = new ArrayList<>();
        appendHistory(messages);
        messages.add(ChatMessage.user(userMessage));

        List<ToolExecution> executions = new ArrayList<>();
        String systemPrompt = buildSystemPrompt();
        List<ToolSchema> toolSchemas = toolRegistry.definitions().stream()
                .map(this::toToolSchema)
                .toList();

        int maxCalls = agentProperties.maxToolCallsPerTurn() > 0 ? agentProperties.maxToolCallsPerTurn() : 5;

        for (int step = 0; step < maxCalls; step++) {
            ChatRequest request = new ChatRequest(
                    systemPrompt,
                    List.copyOf(messages),
                    toolSchemas,
                    llmProperties.temperature(),
                    llmProperties.maxTokens()
            );

            eventBus.publish(new AgentEvents.LLMRequestStarted());
            ChatResponse response;
            try {
                response = llm.chat(request);
            } catch (Exception e) {
                log.error("LLM call failed", e);
                eventBus.publish(new AgentEvents.LLMRequestFailed(e.getMessage()));
                String text = "Przepraszam, nie mogę teraz skorzystać z modelu językowego.";
                sessions.current().addTurn("assistant", text);
                sessions.persist();
                return new AgentResult(text, executions, false);
            }
            eventBus.publish(new AgentEvents.LLMResponseReceived(
                    response.content(),
                    response.toolCalls() == null ? 0 : response.toolCalls().size()));

            // Final answer — no more tools needed.
            if (!response.hasToolCalls()) {
                String text = response.content() == null ? "" : response.content();
                sessions.current().addTurn("assistant", text);
                sessions.persist();
                return new AgentResult(text, executions, true);
            }

            // The LLM requested tools. Record the assistant tool-call message
            // so the next LLM call has the full conversation context.
            messages.add(ChatMessage.assistantToolCalls(response.toolCalls()));

            for (ToolCall toolCall : response.toolCalls()) {
                ToolExecution execution = executeTool(toolCall);
                executions.add(execution);
                messages.add(ChatMessage.tool(toolCall.name(), execution.message()));
            }
        }

        String text = "Wykonano zbyt wiele operacji naraz. Spróbuj prościej.";
        sessions.current().addTurn("assistant", text);
        sessions.persist();
        return new AgentResult(text, executions, false);
    }

    private ToolExecution executeTool(ToolCall call) {
        String toolName = call.name();
        Map<String, Object> arguments = call.arguments() == null ? Map.of() : call.arguments();
        long start = System.currentTimeMillis();

        eventBus.publish(new AgentEvents.ActionStarted(toolName, arguments));

        Action action = toolRegistry.find(toolName).orElse(null);
        if (action == null) {
            eventBus.publish(new AgentEvents.ActionFailed(toolName, "Unknown tool"));
            return ToolExecution.of(toolName, arguments, false,
                    "Unknown tool: " + toolName, elapsed(start));
        }

        if (!permissionManager.isAllowed(action)) {
            eventBus.publish(new AgentEvents.ActionFailed(toolName, "Permission denied"));
            return ToolExecution.of(toolName, arguments, false,
                    "Permission denied for tool: " + toolName, elapsed(start));
        }

        if (confirmationService.requiresConfirmation(action.strategy())) {
            eventBus.publish(new AgentEvents.ConfirmationRequested(toolName, arguments, action.strategy().name()));
            boolean approved = confirmationService.confirm(toolName, arguments, action.strategy());
            eventBus.publish(new AgentEvents.ConfirmationResolved(toolName, approved));
            if (!approved) {
                eventBus.publish(new AgentEvents.ActionFailed(toolName, "Confirmation required"));
                return ToolExecution.of(toolName, arguments, false,
                        "Confirmation required for tool: " + toolName, elapsed(start));
            }
        }

        ActionResult result = toolRegistry.execute(toolName, arguments);
        long duration = elapsed(start);

        if (result.success()) {
            eventBus.publish(new AgentEvents.ActionCompleted(toolName, arguments, result.message()));
            sessions.current().addAction(toolName + " " + arguments + " → " + result.message());
        } else {
            eventBus.publish(new AgentEvents.ActionFailed(toolName, result.message()));
        }

        return ToolExecution.of(toolName, arguments, result.success(), result.message(), duration);
    }

    private ToolSchema toToolSchema(ToolDefinition definition) {
        return new ToolSchema(definition.name(), definition.description(), definition.inputSchema());
    }

    private String buildSystemPrompt() {
        String base = systemPromptProvider.systemPrompt();
        String context = contextAggregator != null
                ? contextAggregator.aggregate(sessions.current())
                : "";
        return context.isBlank() ? base : base + "\n\n" + context;
    }

    /** Prepend recent conversation history (excluding the just-added current turn). */
    private void appendHistory(List<ChatMessage> messages) {
        var conversation = sessions.current().conversation();
        int maxHistory = memoryProperties != null && memoryProperties.conversationMaxTurns() > 0
                ? memoryProperties.conversationMaxTurns()
                : 10;
        if (conversation.size() <= 1) {
            return;
        }
        int start = Math.max(0, conversation.size() - 1 - maxHistory);
        for (int i = start; i < conversation.size() - 1; i++) {
            var turn = conversation.get(i);
            if ("user".equals(turn.role())) {
                messages.add(ChatMessage.user(turn.content()));
            } else if ("assistant".equals(turn.role())) {
                messages.add(ChatMessage.assistant(turn.content()));
            }
        }
    }

    private long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }
}
