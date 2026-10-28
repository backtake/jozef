package com.jozef.integration;

import com.jozef.assistant.AssistantCapability;
import com.jozef.configuration.AgentProperties;
import com.jozef.configuration.LlmProperties;
import com.jozef.configuration.MemoryProperties;
import com.jozef.configuration.ProactiveProperties;
import com.jozef.core.action.ExecutionStrategy;
import com.jozef.core.agent.AgentResult;
import com.jozef.core.agent.AgentRuntime;
import com.jozef.core.builtin.GetTimeAction;
import com.jozef.core.context.DefaultContextAggregator;
import com.jozef.core.context.SystemPromptProvider;
import com.jozef.core.events.Event;
import com.jozef.core.events.EventBus;
import com.jozef.core.events.EventListener;
import com.jozef.core.memory.FileLongTermMemory;
import com.jozef.core.memory.LongTermMemory;
import com.jozef.core.memory.MemoryCapability;
import com.jozef.core.memory.MemoryEntry;
import com.jozef.core.security.AutoDenyConfirmationService;
import com.jozef.core.security.ConfirmationService;
import com.jozef.core.security.PermissionManager;
import com.jozef.core.session.SessionManager;
import com.jozef.core.tool.ToolRegistry;
import com.jozef.core.util.Dates;
import com.jozef.llm.ChatRequest;
import com.jozef.llm.ChatResponse;
import com.jozef.llm.LLMProvider;
import com.jozef.llm.ToolCall;
import com.jozef.proactive.ProactiveAgentService;
import com.jozef.proactive.ProactiveCapability;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests of the agent loop, starting from the user instruction
 * (the dictation/STT stage is intentionally out of scope).
 *
 * The LLM is scripted, but everything else is real: ToolRegistry, memory,
 * timers, confirmation policy, session persistence and the full
 * reasoning→tool-calling→final-answer loop.
 */
class AgentEndToEndTest {

    @TempDir
    Path tempDir;

    /** A scripted LLM that dequeues pre-programmed responses. */
    private static final class ScriptedLlm implements LLMProvider {
        private final Queue<ChatResponse> responses = new ArrayDeque<>();

        ScriptedLlm then(String content, List<ToolCall> calls) {
            responses.add(new ChatResponse(content, calls));
            return this;
        }

        @Override public String name() { return "scripted"; }

        @Override public ChatResponse chat(ChatRequest request) {
            ChatResponse response = responses.poll();
            return response == null ? new ChatResponse("", List.of()) : response;
        }
    }

    private static final class NoopEventBus implements EventBus {
        @Override public void publish(Event event) {}
        @Override public <E extends Event> void register(Class<E> eventType, EventListener<E> listener) {}
    }

    private static final class AutoApproveConfirmationService implements ConfirmationService {
        @Override public boolean requiresConfirmation(ExecutionStrategy strategy) {
            return strategy != ExecutionStrategy.SAFE;
        }
        @Override public boolean confirm(String toolName, Map<String, Object> arguments, ExecutionStrategy strategy) {
            return true;
        }
    }

    private static MemoryEntry note(LongTermMemory memory, String content, String date) {
        return memory.remember(content, List.of(), date);
    }

    private ToolRegistry registryWithMemoryAndTime(LongTermMemory memory) {
        ToolRegistry tools = new ToolRegistry();
        tools.register(new GetTimeAction());
        new MemoryCapability(memory, tools).initialize();
        return tools;
    }

    @Test
    void remembersThenRecallsNoteEndToEnd() {
        LongTermMemory memory = new FileLongTermMemory(tempDir.resolve("memory.json").toString());
        ToolRegistry tools = registryWithMemoryAndTime(memory);

        ScriptedLlm llm = new ScriptedLlm();
        llm.then("", List.of(new ToolCall("remember_note",
                Map.of("content", "Kupić mleko", "date", "today", "tags", "zakupy"))))
           .then("", List.of(new ToolCall("recall_day_notes", Map.of())))
           .then("Zapamiętałem: kupić mleko.", List.of());

        AgentRuntime agent = new AgentRuntime(
                llm, tools,
                (SystemPromptProvider) () -> "You are Jozef.",
                new NoopEventBus(),
                new SessionManager(tempDir.resolve("s1.json").toString()),
                new PermissionManager(),
                new AutoDenyConfirmationService(),
                new AgentProperties(8, 30),
                new LlmProperties("scripted", "m", null, null, 0.7, 100, null),
                new DefaultContextAggregator(memory, 5),
                new MemoryProperties(50, 10, tempDir.resolve("m1.json").toString(), null));

        AgentResult result = agent.process("Zapamiętaj, że mam dziś kupić mleko.");

        assertThat(result.success()).isTrue();
        assertThat(result.text()).isEqualTo("Zapamiętałem: kupić mleko.");
        assertThat(result.toolExecutions()).hasSize(2);
        assertThat(result.toolExecutions().get(0).success()).isTrue();
        assertThat(memory.forDate(Dates.today()))
                .extracting(MemoryEntry::content)
                .contains("Kupić mleko");
    }

    @Test
    void unknownToolFailsButLoopReachesFinalAnswer() {
        LongTermMemory memory = new FileLongTermMemory(tempDir.resolve("memory.json").toString());
        ToolRegistry tools = registryWithMemoryAndTime(memory);

        ScriptedLlm llm = new ScriptedLlm();
        llm.then("", List.of(new ToolCall("nonexistent_tool", Map.of())))
           .then("Nie mogłem tego wykonać.", List.of());

        AgentRuntime agent = new AgentRuntime(
                llm, tools,
                (SystemPromptProvider) () -> "You are Jozef.",
                new NoopEventBus(),
                new SessionManager(tempDir.resolve("s2.json").toString()),
                new PermissionManager(),
                new AutoDenyConfirmationService(),
                new AgentProperties(8, 30),
                new LlmProperties("scripted", "m", null, null, 0.7, 100, null),
                new DefaultContextAggregator(memory, 5),
                new MemoryProperties(50, 10, tempDir.resolve("m2.json").toString(), null));

        AgentResult result = agent.process("zrób coś niemożliwego");

        assertThat(result.success()).isTrue();
        assertThat(result.toolExecutions()).hasSize(1);
        assertThat(result.toolExecutions().get(0).success()).isFalse();
        assertThat(result.toolExecutions().get(0).message()).contains("Unknown tool");
        assertThat(result.text()).isEqualTo("Nie mogłem tego wykonać.");
    }

    @Test
    void confirmActionIsBlockedByAutoDeny() {
        LongTermMemory memory = new FileLongTermMemory(tempDir.resolve("memory.json").toString());
        MemoryEntry seeded = note(memory, "do usunięcia", null);
        ToolRegistry tools = registryWithMemoryAndTime(memory);

        ScriptedLlm llm = new ScriptedLlm();
        llm.then("", List.of(new ToolCall("forget_note", Map.of("note_id", seeded.id()))))
           .then("Nie usunąłem notatki bez potwierdzenia.", List.of());

        AgentRuntime agent = new AgentRuntime(
                llm, tools,
                (SystemPromptProvider) () -> "You are Jozef.",
                new NoopEventBus(),
                new SessionManager(tempDir.resolve("s3.json").toString()),
                new PermissionManager(),
                new AutoDenyConfirmationService(),
                new AgentProperties(8, 30),
                new LlmProperties("scripted", "m", null, null, 0.7, 100, null),
                new DefaultContextAggregator(memory, 5),
                new MemoryProperties(50, 10, tempDir.resolve("m3.json").toString(), null));

        AgentResult result = agent.process("Usuń notatkę.");

        assertThat(result.toolExecutions()).hasSize(1);
        assertThat(result.toolExecutions().get(0).success()).isFalse();
        assertThat(result.toolExecutions().get(0).message()).contains("Confirmation required");
        // The note must still exist — the deny policy protected it.
        assertThat(memory.all()).extracting(MemoryEntry::id).contains(seeded.id());
    }

    @Test
    void confirmActionProceedsWhenApproved() {
        LongTermMemory memory = new FileLongTermMemory(tempDir.resolve("memory.json").toString());
        MemoryEntry seeded = note(memory, "do usunięcia", null);
        ToolRegistry tools = registryWithMemoryAndTime(memory);

        ScriptedLlm llm = new ScriptedLlm();
        llm.then("", List.of(new ToolCall("forget_note", Map.of("note_id", seeded.id()))))
           .then("Usunięto.", List.of());

        AgentRuntime agent = new AgentRuntime(
                llm, tools,
                (SystemPromptProvider) () -> "You are Jozef.",
                new NoopEventBus(),
                new SessionManager(tempDir.resolve("s4.json").toString()),
                new PermissionManager(),
                new AutoApproveConfirmationService(),
                new AgentProperties(8, 30),
                new LlmProperties("scripted", "m", null, null, 0.7, 100, null),
                new DefaultContextAggregator(memory, 5),
                new MemoryProperties(50, 10, tempDir.resolve("m4.json").toString(), null));

        AgentResult result = agent.process("Usuń notatkę.");

        assertThat(result.toolExecutions().get(0).success()).isTrue();
        assertThat(memory.all()).isEmpty();
    }

    @Test
    void dailyBriefingComposesNotesAndTimers() {
        LongTermMemory memory = new FileLongTermMemory(tempDir.resolve("memory.json").toString());
        note(memory, "Plan na dziś: raport", Dates.today());
        ToolRegistry tools = registryWithMemoryAndTime(memory);

        // Real proactive capability (in-memory timer scheduler, no Google watchers).
        ProactiveCapability proactive = new ProactiveCapability(
                new ProactiveProperties(true, 5, 5, 15),
                tools,
                new ProactiveAgentService(null, null, null),
                null);
        proactive.initialize();
        proactive.timerScheduler().schedule("Zadzwoń do dentysty", Duration.ofHours(2));

        new AssistantCapability(memory, null, proactive, tools).initialize();

        ScriptedLlm llm = new ScriptedLlm();
        llm.then("", List.of(new ToolCall("daily_briefing", Map.of("date", "today"))))
           .then("Oto twój plan na dziś.", List.of());

        AgentRuntime agent = new AgentRuntime(
                llm, tools,
                (SystemPromptProvider) () -> "You are Jozef.",
                new NoopEventBus(),
                new SessionManager(tempDir.resolve("s5.json").toString()),
                new PermissionManager(),
                new AutoDenyConfirmationService(),
                new AgentProperties(8, 30),
                new LlmProperties("scripted", "m", null, null, 0.7, 100, null),
                new DefaultContextAggregator(memory, 5),
                new MemoryProperties(50, 10, tempDir.resolve("m5.json").toString(), null));

        AgentResult result = agent.process("Jozef, co mam dziś?");

        assertThat(result.success()).isTrue();
        assertThat(result.toolExecutions()).hasSize(1);
        assertThat(result.toolExecutions().get(0).success()).isTrue();
        assertThat(result.toolExecutions().get(0).message())
                .contains("Plan na dziś: raport")
                .contains("Zadzwoń do dentysty");
    }
}
