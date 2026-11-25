package com.jozef.core;

import com.jozef.automation.actions.PressKeysAction;
import com.jozef.automation.actions.TypeTextAction;
import com.jozef.core.action.ExecutionStrategy;
import com.jozef.core.util.SpokenDatesAccess;
import com.jozef.llm.ChatRequest;
import com.jozef.llm.ChatResponse;
import com.jozef.llm.FallbackLlmProviderAccess;
import com.jozef.llm.LLMProvider;
import com.jozef.llm.LlmException;
import com.jozef.speech.SpeakingMonitor;
import com.jozef.speech.SpeechSynthesizer;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafetyAndUxTest {

    @Test
    void keystrokesThatSubmitOrOpenSystemUiNeedConfirmation() {
        PressKeysAction keys = new PressKeysAction(null);
        assertThat(keys.strategyFor(Map.of("combo", "ctrl+c"))).isEqualTo(ExecutionStrategy.SAFE);
        assertThat(keys.strategyFor(Map.of("combo", "alt+tab"))).isEqualTo(ExecutionStrategy.SAFE);
        assertThat(keys.strategyFor(Map.of("combo", "enter"))).isEqualTo(ExecutionStrategy.CONFIRM);
        assertThat(keys.strategyFor(Map.of("combo", "win+r"))).isEqualTo(ExecutionStrategy.CONFIRM);
        assertThat(keys.strategyFor(Map.of("combo", "Alt + F4"))).isEqualTo(ExecutionStrategy.CONFIRM);

        TypeTextAction type = new TypeTextAction(null);
        assertThat(type.strategyFor(Map.of("text", "Dzień dobry"))).isEqualTo(ExecutionStrategy.SAFE);
        assertThat(type.strategyFor(Map.of("text", "del /q *\n"))).isEqualTo(ExecutionStrategy.CONFIRM);
        assertThat(type.confirmationSummary(Map.of("text", "dir\n"))).contains("[Enter]");
    }

    @Test
    void llmErrorsExplainThemselvesInPolish() {
        assertThat(new LlmException("openai returned HTTP 429: insufficient_quota", 429, null).userHint())
                .contains("skończyły się środki");
        assertThat(new LlmException("x", 401, null).userHint()).contains("klucz API");
        assertThat(new LlmException("ollama unreachable: null").userHint()).contains("Ollama");
        assertThat(new LlmException("openai returned HTTP 429: rate limit", 429, null).isAccountProblem()).isFalse();
    }

    @Test
    void exhaustedAccountIsSkippedForLongAndItsReasonIsReported() {
        AtomicInteger primaryCalls = new AtomicInteger();
        LLMProvider broke = new LLMProvider() {
            @Override public String name() { return "openai"; }
            @Override public ChatResponse chat(ChatRequest r) {
                primaryCalls.incrementAndGet();
                throw new LlmException("openai returned HTTP 429: insufficient_quota", 429, null);
            }
        };
        LLMProvider down = new LLMProvider() {
            @Override public String name() { return "ollama"; }
            @Override public ChatResponse chat(ChatRequest r) { throw new LlmException("ollama unreachable: null"); }
        };
        LLMProvider fallback = FallbackLlmProviderAccess.create(broke, down);
        ChatRequest request = new ChatRequest(null, List.of(), List.of(), 0.1, 10);

        assertThatThrownBy(() -> fallback.chat(request))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.userHint()).contains("środki"));
        assertThatThrownBy(() -> fallback.chat(request)).isInstanceOf(LlmException.class);
        assertThat(primaryCalls).hasValue(1); // second call skipped the exhausted account
    }

    @Test
    void speakingMonitorSerialisesSpeechAndFlagsEcho() throws Exception {
        SpeakingMonitor monitor = new SpeakingMonitor();
        SpeechSynthesizer slow = monitor.guard(new SpeechSynthesizer() {
            @Override public String name() { return "slow"; }
            @Override public void speak(String text) {
                try { Thread.sleep(150); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        });
        long before = System.currentTimeMillis();
        Thread.sleep(5);
        assertThat(monitor.spokeDuring(before, System.currentTimeMillis())).isFalse();

        long listenStart = System.currentTimeMillis();
        slow.speak("Temat: tak, potwierdzam");
        assertThat(monitor.spokeDuring(listenStart, System.currentTimeMillis())).isTrue();

        Thread.sleep(5);
        long laterStart = System.currentTimeMillis();
        assertThat(monitor.spokeDuring(laterStart, laterStart + 1000)).isFalse();
    }

    @Test
    void datesAreSpokenNaturally() {
        LocalDate today = LocalDate.of(2026, 11, 26); // Thursday
        assertThat(SpokenDatesAccess.range("2026-11-27T14:00:00", "2026-11-27T15:30:00", today))
                .isEqualTo("jutro o 14:00 do 15:30");
        assertThat(SpokenDatesAccess.range("2026-12-01T09:00:00", null, today)).isEqualTo("wtorek 1.12 o 9:00");
        assertThat(SpokenDatesAccess.range("nonsense", null, today)).isEqualTo("nonsense");
    }
}
