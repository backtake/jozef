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
import com.jozef.core.security.ConfirmationReply;
import com.jozef.core.security.ConfirmationService;
import com.jozef.core.security.ConversationalConfirmationService;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The agent reasoning loop — the heart of Jozef.
 *
 * <pre>
 *   user message → LLM → normal response? → answer
 *                      └→ tool calls → validate → execute → feed results back → LLM → final response
 * </pre>
 *
 * The Agent is the ONLY orchestrator. Capabilities (browser, gmail, windows, …)
 * are reached exclusively through the {@link ToolRegistry}; the LLM never
 * touches them directly.
 *
 * <p>Risky tools ({@code CONFIRM}/{@code DANGEROUS}) with a
 * {@link ConversationalConfirmationService} are parked as a pending
 * confirmation: the model is told the action was NOT executed and asks the
 * user; only the user's next short yes/no reply (classified deterministically)
 * releases or cancels it.
 */
@Slf4j
public class AgentRuntime implements AutoCloseable {

    static final String LLM_UNAVAILABLE = "Przepraszam, nie mogę teraz połączyć się z modelem językowym. Spróbuj za chwilę.";
    static final String TOO_MANY_STEPS = "Wykonałem już sporo kroków i nie skończyłem. Spróbuj podzielić polecenie na prostsze.";
    static final String CANCELLED = "Dobrze, anulowałem.";
    static final Duration PENDING_TTL = Duration.ofMinutes(3);

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
    private final Clock clock;
    /**
     * Tools run on one dedicated thread (thread-confined libraries such as Playwright
     * and AWT Robot expect a stable thread). A timed-out tool abandons the thread and
     * a fresh executor takes over.
     */
    private ExecutorService toolExecutor = newToolExecutor();

    private static ExecutorService newToolExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "jozef-tool");
            t.setDaemon(true);
            return t;
        });
    }

    /** A risky tool call waiting for the user's yes/no. Only touched under {@code this} lock. */
    private PendingConfirmation pending;

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
        this(llm, toolRegistry, systemPromptProvider, eventBus, sessions, permissionManager,
                confirmationService, agentProperties, llmProperties, contextAggregator,
                memoryProperties, Clock.systemDefaultZone());
    }

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
                        MemoryProperties memoryProperties,
                        Clock clock) {
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
        this.clock = clock;
    }

    /**
     * Process one user utterance and return the final response text.
     * Synchronized so voice, text, phone and proactive triggers never interleave a turn.
     */
    public synchronized AgentResult process(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return AgentResult.reply("");
        }

        eventBus.publish(new AgentEvents.UserCommandReceived(userMessage));
        List<ChatMessage> history = historyMessages();
        sessions.current().addTurn("user", userMessage);

        List<ChatMessage> messages = new ArrayList<>(history);
        messages.add(ChatMessage.user(userMessage));
        List<ToolExecution> executions = new ArrayList<>();

        PendingConfirmation waiting = takePending();
        if (waiting != null) {
            ConfirmationReply.Kind reply = ConfirmationReply.classify(userMessage);
            eventBus.publish(new AgentEvents.ConfirmationResolved(waiting.call().name(), reply == ConfirmationReply.Kind.YES));
            if (reply == ConfirmationReply.Kind.NO) {
                log.info("User cancelled pending '{}'", waiting.call().name());
                return finish(CANCELLED, executions, true);
            }
            if (reply == ConfirmationReply.Kind.YES) {
                log.info("User approved pending '{}'", waiting.call().name());
                ToolExecution execution = runTool(waiting.action(), waiting.call());
                executions.add(execution);
                messages.add(ChatMessage.assistantToolCalls(List.of(waiting.call())));
                messages.add(ChatMessage.toolResult(waiting.call(), resultForModel(execution)));
                return loop(messages, executions);
            }
            log.info("Pending '{}' dropped — user moved on", waiting.call().name());
        }

        return loop(messages, executions);
    }

    /** True while a risky action waits for the user's yes/no. */
    public synchronized boolean hasPendingConfirmation() {
        return pending != null && !pending.isExpired(clock.instant());
    }

    private AgentResult loop(List<ChatMessage> messages, List<ToolExecution> executions) {
        String systemPrompt = buildSystemPrompt();
        List<ToolSchema> toolSchemas = toolRegistry.definitions().stream()
                .map(this::toToolSchema)
                .toList();

        int maxSteps = agentProperties.maxToolCallsPerTurn() > 0 ? agentProperties.maxToolCallsPerTurn() : 5;

        for (int step = 0; step <= maxSteps; step++) {
            // On the last step withhold tools so the model must summarise what it has.
            boolean lastStep = step == maxSteps;
            ChatResponse response = callLlm(systemPrompt, messages, lastStep ? List.of() : toolSchemas);
            if (response == null) {
                return finish(LLM_UNAVAILABLE, executions, false);
            }

            if (!response.hasToolCalls()) {
                String text = response.content() == null ? "" : response.content().trim();
                if (text.isEmpty()) {
                    text = executions.isEmpty() ? "Nie wiem, co odpowiedzieć." : summarize(executions);
                }
                return finish(text, executions, true);
            }
            if (lastStep) {
                break;
            }

            messages.add(ChatMessage.assistantToolCalls(response.content(), response.toolCalls()));
            for (ToolCall toolCall : response.toolCalls()) {
                ToolExecution execution = executeTool(toolCall);
                executions.add(execution);
                messages.add(ChatMessage.toolResult(toolCall, resultForModel(execution)));
            }
        }
        return finish(TOO_MANY_STEPS, executions, false);
    }

    private ChatResponse callLlm(String systemPrompt, List<ChatMessage> messages, List<ToolSchema> tools) {
        ChatRequest request = new ChatRequest(systemPrompt, List.copyOf(messages), tools,
                llmProperties.temperature(), llmProperties.maxTokens());
        eventBus.publish(new AgentEvents.LLMRequestStarted());
        try {
            ChatResponse response = llm.chat(request);
            if (response == null) {
                throw new IllegalStateException("LLM returned no response");
            }
            eventBus.publish(new AgentEvents.LLMResponseReceived(
                    response.content(), response.toolCalls() == null ? 0 : response.toolCalls().size()));
            return response;
        } catch (Exception e) {
            log.error("LLM call failed: {}", e.getMessage(), e);
            eventBus.publish(new AgentEvents.LLMRequestFailed(e.getMessage()));
            return null;
        }
    }

    private AgentResult finish(String text, List<ToolExecution> executions, boolean success) {
        sessions.current().addTurn("assistant", text);
        sessions.persist();
        return new AgentResult(text, executions, success);
    }

    private ToolExecution executeTool(ToolCall call) {
        String toolName = call.name();
        Map<String, Object> arguments = call.arguments();

        Action action = toolRegistry.find(toolName).orElse(null);
        if (action == null) {
            eventBus.publish(new AgentEvents.ActionFailed(toolName, "Unknown tool"));
            return ToolExecution.of(toolName, arguments, false, "Unknown tool: " + toolName, 0);
        }

        if (!permissionManager.isAllowed(action)) {
            eventBus.publish(new AgentEvents.ActionFailed(toolName, "Permission denied"));
            return ToolExecution.of(toolName, arguments, false, "Permission denied for tool: " + toolName, 0);
        }

        if (confirmationService.requiresConfirmation(action.strategy())) {
            eventBus.publish(new AgentEvents.ConfirmationRequested(toolName, arguments, action.strategy().name()));

            if (confirmationService instanceof ConversationalConfirmationService) {
                pending = new PendingConfirmation(call, action, clock.instant().plus(PENDING_TTL));
                log.info("Tool '{}' parked until the user confirms", toolName);
                return ToolExecution.of(toolName, arguments, false,
                        "NOT EXECUTED YET — this action needs the user's confirmation. "
                                + "Briefly tell the user exactly what you are about to do (with the key details) "
                                + "and ask them to answer yes or no. Do not call this tool again.", 0);
            }

            boolean approved = confirmationService.confirm(toolName, arguments, action.strategy());
            eventBus.publish(new AgentEvents.ConfirmationResolved(toolName, approved));
            if (!approved) {
                eventBus.publish(new AgentEvents.ActionFailed(toolName, "Confirmation required"));
                return ToolExecution.of(toolName, arguments, false,
                        "Confirmation required for tool: " + toolName + " — the user did not approve it.", 0);
            }
        }

        return runTool(action, call);
    }

    /** Executes an already-authorised tool with the configured timeout. */
    private ToolExecution runTool(Action action, ToolCall call) {
        String toolName = call.name();
        Map<String, Object> arguments = call.arguments();
        long start = System.currentTimeMillis();
        eventBus.publish(new AgentEvents.ActionStarted(toolName, arguments));

        int timeoutSeconds = agentProperties.toolTimeoutSeconds() > 0 ? agentProperties.toolTimeoutSeconds() : 30;
        ActionResult result;
        Future<ActionResult> future = toolExecutor.submit(() -> toolRegistry.execute(toolName, arguments));
        try {
            result = future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("Tool '{}' timed out after {}s — replacing the tool thread", toolName, timeoutSeconds);
            toolExecutor.shutdownNow();
            toolExecutor = newToolExecutor();
            result = ActionResult.fail("Tool timed out after " + timeoutSeconds + "s");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            result = ActionResult.fail("Interrupted");
        } catch (Exception e) {
            result = ActionResult.fail("Tool failed: " + e.getMessage());
        }
        if (result == null) {
            result = ActionResult.fail("Tool returned no result");
        }

        long duration = System.currentTimeMillis() - start;
        if (result.success()) {
            eventBus.publish(new AgentEvents.ActionCompleted(toolName, arguments, result.message()));
            sessions.current().addAction(toolName + " " + arguments + " → " + result.message());
        } else {
            eventBus.publish(new AgentEvents.ActionFailed(toolName, result.message()));
        }
        return ToolExecution.of(toolName, arguments, result.success(), result.message(), duration);
    }

    private static String resultForModel(ToolExecution execution) {
        String message = execution.message() == null ? "" : execution.message();
        return execution.success() ? message : "FAILED: " + message;
    }

    private static String summarize(List<ToolExecution> executions) {
        ToolExecution last = executions.get(executions.size() - 1);
        return last.success() ? "Gotowe." : "Nie udało się: " + last.message();
    }

    private PendingConfirmation takePending() {
        PendingConfirmation p = pending;
        pending = null;
        if (p != null && p.isExpired(clock.instant())) {
            log.info("Pending confirmation for '{}' expired", p.call().name());
            return null;
        }
        return p;
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

    /** Recent conversation history (before the current utterance) as LLM messages. */
    private List<ChatMessage> historyMessages() {
        var conversation = sessions.current().conversation();
        int maxHistory = memoryProperties != null && memoryProperties.conversationMaxTurns() > 0
                ? memoryProperties.conversationMaxTurns()
                : 10;
        List<ChatMessage> messages = new ArrayList<>();
        int start = Math.max(0, conversation.size() - maxHistory);
        for (int i = start; i < conversation.size(); i++) {
            var turn = conversation.get(i);
            if ("user".equals(turn.role())) {
                messages.add(ChatMessage.user(turn.content()));
            } else if ("assistant".equals(turn.role())) {
                messages.add(ChatMessage.assistant(turn.content()));
            }
        }
        // Providers expect the conversation to start with a user turn.
        while (!messages.isEmpty() && !"user".equals(messages.get(0).role())) {
            messages.remove(0);
        }
        return messages;
    }

    @Override
    public void close() {
        toolExecutor.shutdownNow();
    }

    record PendingConfirmation(ToolCall call, Action action, Instant expiresAt) {
        boolean isExpired(Instant now) {
            return now.isAfter(expiresAt);
        }
    }
}
