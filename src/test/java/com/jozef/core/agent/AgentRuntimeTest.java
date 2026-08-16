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
import com.jozef.core.security.PermissionManager;
import com.jozef.core.session.SessionManager;
import com.jozef.core.tool.ToolRegistry;
import com.jozef.llm.ChatRequest;
import com.jozef.llm.ChatResponse;
import com.jozef.llm.LLMProvider;
import com.jozef.llm.ToolCall;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRuntimeTest {

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
                new SessionManager(),
                new PermissionManager(),
                confirmation,
                new AgentProperties(5, 30),
                new LlmProperties("fake", "m", null, null, 0.7, 100),
                contextAggregator,
                new MemoryProperties(50, 10, null, null));
    }
}
