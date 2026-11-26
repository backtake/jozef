package com.jozef.core.agent;

import com.jozef.configuration.AgentProperties;
import com.jozef.configuration.LlmProperties;
import com.jozef.configuration.MemoryProperties;
import com.jozef.core.action.Action;
import com.jozef.core.action.ActionContext;
import com.jozef.core.action.ActionResult;
import com.jozef.core.action.ExecutionStrategy;
import com.jozef.core.context.ContextAggregator;
import com.jozef.core.context.SystemPromptProvider;
import com.jozef.core.events.Event;
import com.jozef.core.events.EventBus;
import com.jozef.core.events.EventListener;
import com.jozef.core.security.AutoDenyConfirmationService;
import com.jozef.core.security.ConfirmationService;
import com.jozef.core.security.ConversationalConfirmationService;
import com.jozef.core.security.PermissionManager;
import com.jozef.core.session.SessionManager;
import com.jozef.core.tool.ToolRegistry;
import com.jozef.llm.ChatRequest;
import com.jozef.llm.ChatResponse;
import com.jozef.llm.LLMProvider;
import com.jozef.llm.ToolCall;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRuntimeTest {

    @TempDir
    Path tempDir;

    private static class FakeLlm implements LLMProvider {
        private final Queue<ChatResponse> responses = new ArrayDeque<>();
        private ChatRequest lastRequest;

        void enqueue(ChatResponse response) {
            responses.add(response);
        }

        ChatRequest lastRequest() {
            return lastRequest;
        }

        @Override public String name() { return "fake"; }

        @Override public ChatResponse chat(ChatRequest request) {
            this.lastRequest = request;
            return responses.poll();
        }
    }

    private static class RecordingEventBus implements EventBus {
        private final List<Event> events = new ArrayList<>();

        @Override public void publish(Event event) { events.add(event); }

        @Override public <E extends Event> void register(Class<E> eventType, EventListener<E> listener) {}
    }

    private static Action echoAction() {
        return new Action() {
            @Override public String name() { return "echo"; }
            @Override public String description() { return "Echo input"; }
            @Override public ActionResult execute(ActionContext context) {
                return ActionResult.ok("echoed: " + context.getString("value"));
            }
        };
    }

    private static Action confirmAction() {
        return new Action() {
            @Override public String name() { return "send_email"; }
            @Override public String description() { return "Send an email"; }
            @Override public ExecutionStrategy strategy() { return ExecutionStrategy.CONFIRM; }
            @Override public ActionResult execute(ActionContext context) { return ActionResult.ok("sent"); }
        };
    }

    @Test
    void executesToolAndReturnsFinalResponse() {
        FakeLlm llm = new FakeLlm();
        ToolRegistry tools = new ToolRegistry();
        tools.register(echoAction());

        llm.enqueue(new ChatResponse("", List.of(new ToolCall("echo", Map.of("value", "hi")))));
        llm.enqueue(new ChatResponse("Done", List.of()));

        AgentRuntime agent = agentWith(llm, tools, new AutoDenyConfirmationService());
        AgentResult result = agent.process("echo hi");

        assertThat(result.success()).isTrue();
        assertThat(result.text()).isEqualTo("Done");
        assertThat(result.toolExecutions()).hasSize(1);
        assertThat(result.toolExecutions().get(0).success()).isTrue();
        assertThat(result.toolExecutions().get(0).message()).isEqualTo("echoed: hi");
    }

    @Test
    void unknownToolIsReportedAsFailureButLoopContinues() {
        FakeLlm llm = new FakeLlm();
        ToolRegistry tools = new ToolRegistry();

        llm.enqueue(new ChatResponse("", List.of(new ToolCall("nope", Map.of()))));
        llm.enqueue(new ChatResponse("I could not do that", List.of()));

        AgentRuntime agent = agentWith(llm, tools, new AutoDenyConfirmationService());
        AgentResult result = agent.process("do something");

        assertThat(result.toolExecutions()).hasSize(1);
        assertThat(result.toolExecutions().get(0).success()).isFalse();
        assertThat(result.toolExecutions().get(0).message()).contains("Unknown tool");
        assertThat(result.text()).isEqualTo("I could not do that");
    }

    @Test
    void confirmStrategyIsDeniedByDefault() {
        FakeLlm llm = new FakeLlm();
        ToolRegistry tools = new ToolRegistry();
        tools.register(confirmAction());

        llm.enqueue(new ChatResponse("", List.of(new ToolCall("send_email", Map.of("to", "x")))));
        llm.enqueue(new ChatResponse("I need your confirmation", List.of()));

        AgentRuntime agent = agentWith(llm, tools, new AutoDenyConfirmationService());
        AgentResult result = agent.process("send an email");

        assertThat(result.toolExecutions()).hasSize(1);
        assertThat(result.toolExecutions().get(0).success()).isFalse();
        assertThat(result.toolExecutions().get(0).message()).contains("Confirmation required");
    }

    @Test
    void passesConversationHistoryToLlm() {
        FakeLlm llm = new FakeLlm();
        ToolRegistry tools = new ToolRegistry();
        llm.enqueue(new ChatResponse("First answer", List.of()));
        llm.enqueue(new ChatResponse("Second answer", List.of()));

        AgentRuntime agent = agentWith(llm, tools, new AutoDenyConfirmationService());
        agent.process("first question");
        agent.process("second question");

        var messages = llm.lastRequest().messages();
        assertThat(messages).hasSize(3);
        assertThat(messages.get(0).role()).isEqualTo("user");
        assertThat(messages.get(0).content()).isEqualTo("first question");
        assertThat(messages.get(1).role()).isEqualTo("assistant");
        assertThat(messages.get(1).content()).isEqualTo("First answer");
        assertThat(messages.get(2).role()).isEqualTo("user");
        assertThat(messages.get(2).content()).isEqualTo("second question");
    }

    @Test
    void appendsAggregatedContextToSystemPrompt() {
        FakeLlm llm = new FakeLlm();
        ToolRegistry tools = new ToolRegistry();
        llm.enqueue(new ChatResponse("ok", List.of()));

        AgentRuntime agent = agentWith(llm, tools, new AutoDenyConfirmationService(),
                session -> "CONTEXT_MARKER");
        agent.process("hello");

        assertThat(llm.lastRequest().systemPrompt()).contains("CONTEXT_MARKER");
    }

    // ─── Conversational confirmation ────────────────────────────

    private static class CountingConfirmAction implements Action {
        int executions;
        @Override public String name() { return "send_email"; }
        @Override public String description() { return "Send an email"; }
        @Override public ExecutionStrategy strategy() { return ExecutionStrategy.CONFIRM; }
        @Override public ActionResult execute(ActionContext context) {
            executions++;
            return ActionResult.ok("sent to " + context.getString("to"));
        }
    }

    @Test
    void riskyToolIsParkedUntilUserSaysYes() {
        FakeLlm llm = new FakeLlm();
        ToolRegistry tools = new ToolRegistry();
        CountingConfirmAction send = new CountingConfirmAction();
        tools.register(send);

        llm.enqueue(new ChatResponse("", List.of(ToolCall.of("c1", "send_email", Map.of("to", "ania@example.com")))));
        llm.enqueue(new ChatResponse("Mam wysłać maila do Ani?", List.of()));
        AgentRuntime agent = agentWith(llm, tools, new ConversationalConfirmationService());

        AgentResult ask = agent.process("wyślij Ani maila");
        // The question is built by Jozef from the real arguments, not taken from the model.
        assertThat(ask.text()).contains("send_email").contains("ania@example.com").contains("tak albo nie");
        assertThat(send.executions).isZero();
        assertThat(agent.hasPendingConfirmation()).isTrue();
        assertThat(llm.lastRequest().messages().get(2).content()).contains("NOT EXECUTED YET");

        llm.enqueue(new ChatResponse("Wysłane.", List.of()));
        AgentResult done = agent.process("Tak, wyślij");

        assertThat(send.executions).isEqualTo(1);
        assertThat(done.text()).isEqualTo("Wysłane.");
        assertThat(done.toolExecutions()).singleElement().satisfies(e -> {
            assertThat(e.success()).isTrue();
            assertThat(e.message()).isEqualTo("sent to ania@example.com");
        });
        assertThat(agent.hasPendingConfirmation()).isFalse();
        // The model sees the approved call and its result, correlated by id.
        var sent = llm.lastRequest().messages();
        assertThat(sent.get(sent.size() - 1).toolCallId()).isEqualTo("c1");
    }

    @Test
    void userCanCancelParkedTool() {
        FakeLlm llm = new FakeLlm();
        ToolRegistry tools = new ToolRegistry();
        CountingConfirmAction send = new CountingConfirmAction();
        tools.register(send);

        llm.enqueue(new ChatResponse("", List.of(ToolCall.of("send_email", Map.of("to", "x")))));
        llm.enqueue(new ChatResponse("Wysłać?", List.of()));
        AgentRuntime agent = agentWith(llm, tools, new ConversationalConfirmationService());
        agent.process("wyślij maila");

        AgentResult result = agent.process("nie");

        assertThat(send.executions).isZero();
        assertThat(result.text()).isEqualTo(AgentRuntime.CANCELLED);
        assertThat(agent.hasPendingConfirmation()).isFalse();
    }

    @Test
    void unrelatedReplyDropsParkedToolAndIsHandledNormally() {
        FakeLlm llm = new FakeLlm();
        ToolRegistry tools = new ToolRegistry();
        CountingConfirmAction send = new CountingConfirmAction();
        tools.register(send);

        llm.enqueue(new ChatResponse("", List.of(ToolCall.of("send_email", Map.of("to", "x")))));
        llm.enqueue(new ChatResponse("Wysłać?", List.of()));
        AgentRuntime agent = agentWith(llm, tools, new ConversationalConfirmationService());
        agent.process("wyślij maila");

        llm.enqueue(new ChatResponse("Jest 12:00.", List.of()));
        AgentResult result = agent.process("która godzina");

        assertThat(send.executions).isZero();
        assertThat(result.text()).isEqualTo("Jest 12:00.");
        // A later "tak" must not resurrect the dropped action.
        llm.enqueue(new ChatResponse("Na co tak?", List.of()));
        agent.process("tak");
        assertThat(send.executions).isZero();
    }

    @Test
    void modelCannotApproveItsOwnActionByCallingTheToolTwice() {
        FakeLlm llm = new FakeLlm();
        ToolRegistry tools = new ToolRegistry();
        CountingConfirmAction send = new CountingConfirmAction();
        tools.register(send);

        llm.enqueue(new ChatResponse("", List.of(ToolCall.of("send_email", Map.of("to", "x")))));
        llm.enqueue(new ChatResponse("", List.of(ToolCall.of("send_email", Map.of("to", "x")))));
        llm.enqueue(new ChatResponse("Wysłać?", List.of()));
        AgentRuntime agent = agentWith(llm, tools, new ConversationalConfirmationService());
        agent.process("wyślij maila");

        assertThat(send.executions).isZero();
    }

    @Test
    void modelTextCannotMisrepresentWhatWillBeApproved() {
        FakeLlm llm = new FakeLlm();
        ToolRegistry tools = new ToolRegistry();
        tools.register(new CountingConfirmAction());

        llm.enqueue(new ChatResponse("", List.of(ToolCall.of("send_email", Map.of("to", "attacker@evil.example")))));
        llm.enqueue(new ChatResponse("Mam zapisać notatkę?", List.of()));
        AgentRuntime agent = agentWith(llm, tools, new ConversationalConfirmationService());

        AgentResult ask = agent.process("zrób porządek w notatkach");

        assertThat(ask.text()).contains("attacker@evil.example").doesNotContain("notatkę");
    }

    @Test
    void parkedActionIsDroppedWhenTheTurnFailsSoALaterOkDoesNothing() {
        FakeLlm llm = new FakeLlm();
        ToolRegistry tools = new ToolRegistry();
        CountingConfirmAction send = new CountingConfirmAction();
        tools.register(send);

        llm.enqueue(new ChatResponse("", List.of(ToolCall.of("send_email", Map.of("to", "x")))));
        // next LLM call returns null → treated as provider failure
        AgentRuntime agent = agentWith(llm, tools, new ConversationalConfirmationService());

        AgentResult failed = agent.process("wyślij maila");
        assertThat(failed.text()).isEqualTo(AgentRuntime.LLM_UNAVAILABLE);
        assertThat(agent.hasPendingConfirmation()).isFalse();

        llm.enqueue(new ChatResponse("Okej.", List.of()));
        agent.process("ok");
        assertThat(send.executions).isZero();
    }

    @Test
    void severalRiskyCallsAreConfirmedAndExecutedTogether() {
        FakeLlm llm = new FakeLlm();
        ToolRegistry tools = new ToolRegistry();
        CountingConfirmAction send = new CountingConfirmAction();
        tools.register(send);

        llm.enqueue(new ChatResponse("", List.of(
                ToolCall.of("a", "send_email", Map.of("to", "ania@example.com")),
                ToolCall.of("b", "send_email", Map.of("to", "bartek@example.com")))));
        llm.enqueue(new ChatResponse("Wysłać oba?", List.of()));
        AgentRuntime agent = agentWith(llm, tools, new ConversationalConfirmationService());

        AgentResult ask = agent.process("wyślij obu");
        assertThat(ask.text()).contains("2 działania").contains("ania@example.com").contains("bartek@example.com");

        llm.enqueue(new ChatResponse("Wysłałem oba.", List.of()));
        AgentResult done = agent.process("tak");
        assertThat(send.executions).isEqualTo(2);
        assertThat(done.toolExecutions()).hasSize(2);
    }

    @Test
    void parkedActionExpires() {
        FakeLlm llm = new FakeLlm();
        ToolRegistry tools = new ToolRegistry();
        CountingConfirmAction send = new CountingConfirmAction();
        tools.register(send);
        MutableClock clock = new MutableClock();

        llm.enqueue(new ChatResponse("", List.of(ToolCall.of("send_email", Map.of("to", "x")))));
        llm.enqueue(new ChatResponse("?", List.of()));
        AgentRuntime agent = new AgentRuntime(llm, tools, () -> "sys", new RecordingEventBus(),
                new SessionManager(tempDir.resolve("s.json").toString()), new PermissionManager(),
                new ConversationalConfirmationService(), new AgentProperties(5, 30),
                new LlmProperties("x", "m", null, null, 0.7, 100, null), null,
                new MemoryProperties(50, 10, null, null), clock);
        agent.process("wyślij");
        assertThat(agent.hasPendingConfirmation()).isTrue();

        clock.advance(AgentRuntime.PENDING_TTL.plusSeconds(1));
        assertThat(agent.hasPendingConfirmation()).isFalse();
        llm.enqueue(new ChatResponse("Na co?", List.of()));
        agent.process("tak");
        assertThat(send.executions).isZero();
    }

    @Test
    void assistantNoticesNeverResolveAPendingConfirmation() {
        FakeLlm llm = new FakeLlm();
        ToolRegistry tools = new ToolRegistry();
        CountingConfirmAction send = new CountingConfirmAction();
        tools.register(send);

        llm.enqueue(new ChatResponse("", List.of(ToolCall.of("send_email", Map.of("to", "x")))));
        llm.enqueue(new ChatResponse("?", List.of()));
        AgentRuntime agent = agentWith(llm, tools, new ConversationalConfirmationService());
        agent.process("wyślij");

        agent.recordAssistantNotice("Przypomnienie: tak");

        assertThat(send.executions).isZero();
        assertThat(agent.hasPendingConfirmation()).isTrue();
    }

    @Test
    void fastPathHandlesCommandWithoutCallingTheModel() {
        FakeLlm llm = new FakeLlm();
        ToolRegistry tools = new ToolRegistry();
        tools.register(echoAction());
        AgentRuntime agent = agentWith(llm, tools, new AutoDenyConfirmationService());
        agent.addFastPath(text -> text.contains("echo")
                ? java.util.Optional.of(new FastPath.Resolved(ToolCall.of("echo", Map.of("value", "x")), "Zrobione."))
                : java.util.Optional.empty());

        AgentResult result = agent.process("zrób echo");

        assertThat(result.text()).isEqualTo("Zrobione.");
        assertThat(result.toolExecutions()).singleElement().satisfies(e -> assertThat(e.message()).isEqualTo("echoed: x"));
        assertThat(llm.lastRequest()).isNull();
    }

    @Test
    void fastPathNeverBypassesConfirmation() {
        FakeLlm llm = new FakeLlm();
        ToolRegistry tools = new ToolRegistry();
        CountingConfirmAction send = new CountingConfirmAction();
        tools.register(send);
        llm.enqueue(new ChatResponse("Model handled it", List.of()));
        AgentRuntime agent = agentWith(llm, tools, new ConversationalConfirmationService());
        agent.addFastPath(text -> java.util.Optional.of(
                new FastPath.Resolved(ToolCall.of("send_email", Map.of("to", "x")), "Wysłane.")));

        AgentResult result = agent.process("wyślij");

        assertThat(send.executions).isZero();
        assertThat(result.text()).isEqualTo("Model handled it");
    }

    @Test
    void approvedActionFollowedByLlmFailureDoesNotClaimNothingHappened() {
        FakeLlm llm = new FakeLlm();
        ToolRegistry tools = new ToolRegistry();
        CountingConfirmAction send = new CountingConfirmAction();
        tools.register(send);
        llm.enqueue(new ChatResponse("", List.of(ToolCall.of("send_email", Map.of("to", "x")))));
        llm.enqueue(new ChatResponse("?", List.of()));
        AgentRuntime agent = agentWith(llm, tools, new ConversationalConfirmationService());
        agent.process("wyślij");

        AgentResult done = agent.process("tak"); // queue is empty now → the follow-up LLM call fails

        assertThat(send.executions).isEqualTo(1);
        assertThat(done.text()).isEqualTo(AgentRuntime.DONE_BUT_LLM_UNAVAILABLE);
    }

    @Test
    void correctionAfterYesIsNotAnApproval() {
        FakeLlm llm = new FakeLlm();
        ToolRegistry tools = new ToolRegistry();
        CountingConfirmAction send = new CountingConfirmAction();
        tools.register(send);
        llm.enqueue(new ChatResponse("", List.of(ToolCall.of("send_email", Map.of("to", "ania@example.com")))));
        llm.enqueue(new ChatResponse("?", List.of()));
        AgentRuntime agent = agentWith(llm, tools, new ConversationalConfirmationService());
        agent.process("wyślij Ani");

        llm.enqueue(new ChatResponse("Do Basi? Podaj adres.", List.of()));
        agent.process("tak, wyślij do Basi");

        assertThat(send.executions).isZero();
    }

    private static class MutableClock extends java.time.Clock {
        private java.time.Instant now = java.time.Instant.parse("2026-11-26T10:00:00Z");
        void advance(java.time.Duration d) { now = now.plus(d); }
        @Override public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
        @Override public java.time.Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public java.time.Instant instant() { return now; }
    }

    // ─── Robustness ──────────────────────────────────────────────

    @Test
    void llmFailureGivesFriendlyMessageNotTheRawError() {
        LLMProvider broken = new LLMProvider() {
            @Override public String name() { return "broken"; }
            @Override public ChatResponse chat(ChatRequest request) {
                throw new com.jozef.llm.LlmException("openai returned HTTP 401: invalid key", 401, null);
            }
        };
        AgentRuntime agent = new AgentRuntime(broken, new ToolRegistry(), () -> "sys", new RecordingEventBus(),
                new SessionManager(tempDir.resolve("s.json").toString()), new PermissionManager(),
                new AutoDenyConfirmationService(), new AgentProperties(5, 30),
                new LlmProperties("x", "m", null, null, 0.7, 100, null), null, new MemoryProperties(50, 10, null, null));

        AgentResult result = agent.process("cześć");

        assertThat(result.success()).isFalse();
        assertThat(result.text()).startsWith("Przepraszam").contains("klucz API").doesNotContain("401");
    }

    @Test
    void hangingToolTimesOutAndLoopContinues() {
        FakeLlm llm = new FakeLlm();
        ToolRegistry tools = new ToolRegistry();
        tools.register(new Action() {
            @Override public String name() { return "slow"; }
            @Override public String description() { return "never returns"; }
            @Override public ActionResult execute(ActionContext context) {
                try { Thread.sleep(60_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return ActionResult.ok("late");
            }
        });
        tools.register(echoAction());

        llm.enqueue(new ChatResponse("", List.of(ToolCall.of("slow", Map.of()))));
        llm.enqueue(new ChatResponse("", List.of(ToolCall.of("echo", Map.of("value", "after")))));
        llm.enqueue(new ChatResponse("Nie zdążyłem", List.of()));

        AgentRuntime agent = new AgentRuntime(llm, tools, () -> "sys", new RecordingEventBus(),
                new SessionManager(tempDir.resolve("s.json").toString()), new PermissionManager(),
                new AutoDenyConfirmationService(), new AgentProperties(5, 1),
                new LlmProperties("x", "m", null, null, 0.7, 100, null), null, new MemoryProperties(50, 10, null, null));

        long start = System.currentTimeMillis();
        AgentResult result = agent.process("zrób coś wolnego");

        assertThat(System.currentTimeMillis() - start).isLessThan(10_000);
        assertThat(result.toolExecutions().get(0).success()).isFalse();
        assertThat(result.toolExecutions().get(0).message()).contains("timed out");
        // A fresh tool thread still serves later tools.
        assertThat(result.toolExecutions().get(1).message()).isEqualTo("echoed: after");
        agent.close();
    }

    @Test
    void lastStepWithholdsToolsSoTheModelMustAnswer() {
        FakeLlm llm = new FakeLlm();
        ToolRegistry tools = new ToolRegistry();
        tools.register(echoAction());
        llm.enqueue(new ChatResponse("", List.of(ToolCall.of("echo", Map.of("value", "1")))));
        llm.enqueue(new ChatResponse("", List.of(ToolCall.of("echo", Map.of("value", "2")))));
        llm.enqueue(new ChatResponse("Podsumowanie", List.of()));

        AgentRuntime agent = new AgentRuntime(llm, tools, () -> "sys", new RecordingEventBus(),
                new SessionManager(tempDir.resolve("s.json").toString()), new PermissionManager(),
                new AutoDenyConfirmationService(), new AgentProperties(2, 30),
                new LlmProperties("x", "m", null, null, 0.7, 100, null), null, new MemoryProperties(50, 10, null, null));

        AgentResult result = agent.process("rób w kółko");

        assertThat(result.text()).isEqualTo("Podsumowanie");
        assertThat(llm.lastRequest().toolCallsAllowed()).isFalse();
        assertThat(llm.lastRequest().tools()).isNotEmpty(); // kept: some APIs require them when history has tool calls
    }

    private AgentRuntime agentWith(FakeLlm llm, ToolRegistry tools, ConfirmationService confirmation) {
        return agentWith(llm, tools, confirmation, null);
    }

    private AgentRuntime agentWith(FakeLlm llm, ToolRegistry tools, ConfirmationService confirmation,
                                   ContextAggregator contextAggregator) {
        return new AgentRuntime(
                llm,
                tools,
                (SystemPromptProvider) () -> "You are Jozef.",
                new RecordingEventBus(),
                new SessionManager(tempDir.resolve("session.json").toString()),
                new PermissionManager(),
                confirmation,
                new AgentProperties(5, 30),
                new LlmProperties("fake", "m", null, null, 0.7, 100, null),
                contextAggregator,
                new MemoryProperties(50, 10, null, null));
    }
}
