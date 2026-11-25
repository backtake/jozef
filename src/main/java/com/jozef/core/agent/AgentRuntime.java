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
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The agent reasoning loop — the heart of Jozef.
 *
 * <pre>
 *   user message → fast paths? → LLM → normal response? → answer
 *                                    └→ tool calls → validate → execute → results → LLM → final response
 * </pre>
 *
 * The Agent is the ONLY orchestrator. Capabilities (browser, gmail, windows, …)
 * are reached exclusively through the {@link ToolRegistry}; the LLM never
 * touches them directly.
 *
 * <h2>Confirmation of risky tools</h2>
 * With a {@link ConversationalConfirmationService}, {@code CONFIRM}/{@code DANGEROUS}
 * calls are parked instead of executed. The question the user hears is built
 * <b>in code</b> from the parked calls and their arguments (never written by the
 * model), and only the user's next reply — classified deterministically by
 * {@link ConfirmationReply} — can release them. A parked action survives only
 * when the turn ends normally with that question; every other exit drops it.
 */
@Slf4j
public class AgentRuntime implements AutoCloseable {

    static final String LLM_UNAVAILABLE = "Przepraszam, nie mogę teraz połączyć się z modelem językowym. Spróbuj za chwilę.";
    static final String TOO_MANY_STEPS = "Wykonałem już sporo kroków i nie skończyłem. Spróbuj podzielić polecenie na prostsze.";
    static final String CANCELLED = "Dobrze, anulowałem.";
    static final Duration PENDING_TTL = Duration.ofMinutes(3);
    private static final int MAX_PARKED = 3;

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
    private final List<FastPath> fastPaths = new CopyOnWriteArrayList<>();

    /**
     * Tools run on one dedicated thread (thread-confined libraries such as Playwright
     * and AWT Robot expect a stable thread). A timed-out tool abandons the thread and
     * a fresh executor takes over.
     */
    private volatile ExecutorService toolExecutor = newToolExecutor();

    /** Risky tool calls waiting for the user's yes/no. Written under {@code this} lock. */
    private volatile PendingConfirmation pending;

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

    private static ExecutorService newToolExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "jozef-tool");
            t.setDaemon(true);
            return t;
        });
    }

    /** Registers a deterministic handler tried (in order) before the language model. */
    public void addFastPath(FastPath fastPath) {
        fastPaths.add(fastPath);
    }

    /**
     * Process one utterance <b>from the user</b> and return the reply.
     * Only call this with text the user actually said or typed — it may approve
     * a pending action. Synchronized so channels never interleave a turn.
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
            ConfirmationReply.Kind reply = ConfirmationReply.classify(userMessage, waiting.toolNames());
            if (reply == ConfirmationReply.Kind.NO) {
                eventBus.publish(new AgentEvents.ConfirmationResolved(waiting.describeTools(), false));
                log.info("User cancelled pending {}", waiting.describeTools());
                return finish(CANCELLED, executions, true);
            }
            if (reply == ConfirmationReply.Kind.YES) {
                eventBus.publish(new AgentEvents.ConfirmationResolved(waiting.describeTools(), true));
                log.info("User approved pending {}", waiting.describeTools());
                List<ToolCall> calls = waiting.calls().stream().map(Parked::call).toList();
                messages.add(ChatMessage.assistantToolCalls(calls));
                for (Parked parked : waiting.calls()) {
                    ToolExecution execution = runTool(parked.action(), parked.call());
                    executions.add(execution);
                    messages.add(ChatMessage.toolResult(parked.call(), resultForModel(execution, parked.action())));
                }
                return loop(messages, executions);
            }
            log.info("Pending {} dropped — the user moved on", waiting.describeTools());
        }

        for (FastPath fastPath : fastPaths) {
            Optional<FastPath.Handled> handled;
            try {
                handled = fastPath.tryHandle(userMessage);
            } catch (Exception e) {
                log.warn("Fast path {} failed: {}", fastPath.getClass().getSimpleName(), e.getMessage());
                continue;
            }
            if (handled.isPresent()) {
                ToolExecution execution = handled.get().execution();
                executions.add(execution);
                recordOutcome(execution);
                return finish(handled.get().reply(), executions, execution.success());
            }
        }

        return loop(messages, executions);
    }

    /**
     * Records something Jozef said on its own (a reminder, a new-mail notice) in the
     * conversation, so a follow-up like "przeczytaj go" has context. It never reaches
     * the model as a user instruction and never touches pending confirmations.
     */
    public synchronized void recordAssistantNotice(String text) {
        if (text != null && !text.isBlank()) {
            sessions.current().addTurn("assistant", text);
            sessions.persist();
        }
    }

    /** True while a risky action waits for the user's yes/no. Lock-free (safe from UI/voice threads). */
    public boolean hasPendingConfirmation() {
        PendingConfirmation p = pending;
        return p != null && !p.isExpired(clock.instant());
    }

    private AgentResult loop(List<ChatMessage> messages, List<ToolExecution> executions) {
        String systemPrompt = buildSystemPrompt();
        List<ToolSchema> toolSchemas = toolRegistry.definitions().stream()
                .map(this::toToolSchema)
                .toList();
        List<Parked> parked = new ArrayList<>();

        int maxSteps = agentProperties.maxToolCallsPerTurn() > 0 ? agentProperties.maxToolCallsPerTurn() : 5;

        for (int step = 0; step <= maxSteps; step++) {
            // On the last step withhold tools so the model must summarise what it has.
            boolean lastStep = step == maxSteps;
            ChatResponse response = callLlm(systemPrompt, messages, lastStep ? List.of() : toolSchemas);
            if (response == null) {
                return finish(LLM_UNAVAILABLE, executions, false);
            }

            if (!response.hasToolCalls() || lastStep) {
                String text = response.content() == null ? "" : response.content().trim();
                if (!parked.isEmpty()) {
                    return park(parked, text, executions);
                }
                if (text.isEmpty()) {
                    if (lastStep && response.hasToolCalls()) {
                        return finish(TOO_MANY_STEPS, executions, false);
                    }
                    text = executions.isEmpty() ? "Nie wiem, co odpowiedzieć." : summarize(executions);
                }
                return finish(text, executions, true);
            }

            messages.add(ChatMessage.assistantToolCalls(response.content(), response.toolCalls()));
            for (ToolCall toolCall : response.toolCalls()) {
                ToolExecution execution = executeTool(toolCall, parked);
                executions.add(execution);
                Action action = toolRegistry.find(toolCall.name()).orElse(null);
                messages.add(ChatMessage.toolResult(toolCall, resultForModel(execution, action)));
            }
        }
        return finish(TOO_MANY_STEPS, executions, false);
    }

    /** Ends the turn with the code-built confirmation question for the parked calls. */
    private AgentResult park(List<Parked> parked, String modelText, List<ToolExecution> executions) {
        pending = new PendingConfirmation(List.copyOf(parked), clock.instant().plus(PENDING_TTL));
        StringBuilder question = new StringBuilder();
        if (parked.size() == 1) {
            question.append("Potwierdź: ").append(parked.get(0).summary()).append(".");
        } else {
            question.append("Potwierdź ").append(parked.size()).append(" działania: ");
            for (int i = 0; i < parked.size(); i++) {
                question.append(i + 1).append(") ").append(parked.get(i).summary()).append(i + 1 < parked.size() ? "; " : ".");
            }
        }
        question.append(" Mam to zrobić? Powiedz tak albo nie.");
        log.info("Asking the user to confirm {} (model text was: {})", pending.describeTools(), modelText);
        sessions.current().addTurn("assistant", question.toString());
        sessions.persist();
        return new AgentResult(question.toString(), executions, true);
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
        // Any exit other than an explicit confirmation question drops parked actions.
        pending = null;
        sessions.current().addTurn("assistant", text);
        sessions.persist();
        return new AgentResult(text, executions, success);
    }

    private ToolExecution executeTool(ToolCall call, List<Parked> parked) {
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
                boolean duplicate = parked.stream().anyMatch(p ->
                        p.call().name().equals(toolName) && p.call().arguments().equals(arguments));
                if (!duplicate) {
                    if (parked.size() >= MAX_PARKED) {
                        return ToolExecution.of(toolName, arguments, false,
                                "Too many actions waiting for confirmation in one request — do this one later.", 0);
                    }
                    parked.add(new Parked(call, action, summaryOf(action, arguments)));
                }
                log.info("Tool '{}' parked until the user confirms", toolName);
                return ToolExecution.of(toolName, arguments, false,
                        "NOT EXECUTED YET — awaiting the user's confirmation, which Jozef will ask for. "
                                + "Do not call this tool again in this turn; just reply briefly.", 0);
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

    private static String summaryOf(Action action, Map<String, Object> arguments) {
        String summary;
        try {
            summary = action.confirmationSummary(arguments);
        } catch (Exception e) {
            summary = null;
        }
        return summary == null || summary.isBlank() ? Action.defaultConfirmationSummary(action.name(), arguments) : summary;
    }

    /** Executes an already-authorised tool with its timeout. */
    private ToolExecution runTool(Action action, ToolCall call) {
        String toolName = call.name();
        Map<String, Object> arguments = call.arguments();
        long start = System.currentTimeMillis();
        eventBus.publish(new AgentEvents.ActionStarted(toolName, arguments));

        int timeoutSeconds = action.timeoutSeconds() > 0 ? action.timeoutSeconds()
                : agentProperties.toolTimeoutSeconds() > 0 ? agentProperties.toolTimeoutSeconds() : 30;
        ActionResult result;
        ExecutorService executor = toolExecutor;
        Future<ActionResult> future = executor.submit(() -> toolRegistry.execute(toolName, arguments));
        try {
            result = future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("Tool '{}' timed out after {}s — replacing the tool thread", toolName, timeoutSeconds);
            executor.shutdownNow();
            toolExecutor = newToolExecutor();
            result = ActionResult.fail(action.strategy() == com.jozef.core.action.ExecutionStrategy.SAFE
                    ? "Tool timed out after " + timeoutSeconds + "s"
                    : "OUTCOME UNKNOWN — no answer within " + timeoutSeconds
                    + "s; the action may still complete. Do NOT retry it; tell the user to check.");
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

        ToolExecution execution = ToolExecution.of(toolName, arguments, result.success(), result.message(),
                System.currentTimeMillis() - start);
        recordOutcome(execution);
        return execution;
    }

    private void recordOutcome(ToolExecution execution) {
        if (execution.success()) {
            eventBus.publish(new AgentEvents.ActionCompleted(execution.tool(), execution.arguments(), execution.message()));
            sessions.current().addAction(execution.tool() + " " + execution.arguments() + " → " + execution.message());
        } else {
            eventBus.publish(new AgentEvents.ActionFailed(execution.tool(), execution.message()));
        }
    }

    private static String resultForModel(ToolExecution execution, Action action) {
        String message = execution.message() == null ? "" : execution.message();
        if (execution.success()) return message;
        return message.startsWith("NOT EXECUTED YET") || message.startsWith("OUTCOME UNKNOWN")
                ? message : "FAILED: " + message;
    }

    private static String summarize(List<ToolExecution> executions) {
        ToolExecution last = executions.get(executions.size() - 1);
        return last.success() ? "Gotowe." : "Niestety, to się nie udało.";
    }

    private synchronized PendingConfirmation takePending() {
        PendingConfirmation p = pending;
        pending = null;
        if (p != null && p.isExpired(clock.instant())) {
            log.info("Pending confirmation for {} expired", p.describeTools());
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
    public synchronized void close() {
        toolExecutor.shutdownNow();
    }

    record Parked(ToolCall call, Action action, String summary) {}

    record PendingConfirmation(List<Parked> calls, Instant expiresAt) {
        boolean isExpired(Instant now) {
            return now.isAfter(expiresAt);
        }

        List<String> toolNames() {
            return calls.stream().map(p -> p.call().name()).toList();
        }

        String describeTools() {
            return String.join(",", toolNames());
        }
    }
}
