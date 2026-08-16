package com.jozef.core.events;

import java.util.Map;

/**
 * Events emitted by the agent loop, voice pipeline, and tool execution.
 */
public final class AgentEvents {

    private AgentEvents() {}

    // ─── Input / voice ────────────────────────────────

    public record ListeningStarted() implements Event {
        @Override public String description() { return "ListeningStarted"; }
    }

    public record SpeechRecognized(String text) implements Event {
        @Override public String description() { return "SpeechRecognized(\"" + text + "\")"; }
    }

    public record SpeechRecognitionFailed(String error) implements Event {
        @Override public String description() { return "SpeechRecognitionFailed(" + error + ")"; }
    }

    public record UserCommandReceived(String text) implements Event {
        @Override public String description() { return "UserCommandReceived(\"" + text + "\")"; }
    }

    // ─── LLM ──────────────────────────────────────────

    public record LLMRequestStarted() implements Event {
        @Override public String description() { return "LLMRequestStarted"; }
    }

    public record LLMResponseReceived(String content, int toolCallCount) implements Event {
        @Override public String description() {
            return "LLMResponseReceived(toolCalls=" + toolCallCount + ")";
        }
    }

    public record LLMRequestFailed(String error) implements Event {
        @Override public String description() { return "LLMRequestFailed(" + error + ")"; }
    }

    // ─── Tool / action execution ──────────────────────

    public record ActionStarted(String tool, Map<String, Object> arguments) implements Event {
        @Override public String description() { return "ActionStarted(" + tool + ")"; }
    }

    public record ActionCompleted(String tool, Map<String, Object> arguments, String result) implements Event {
        @Override public String description() { return "ActionCompleted(" + tool + ")"; }
    }

    public record ActionFailed(String tool, String error) implements Event {
        @Override public String description() { return "ActionFailed(" + tool + ": " + error + ")"; }
    }

    // ─── Confirmation ────────────────────────────────

    public record ConfirmationRequested(String tool, Map<String, Object> arguments, String strategy) implements Event {
        @Override public String description() { return "ConfirmationRequested(" + tool + ", " + strategy + ")"; }
    }

    public record ConfirmationResolved(String tool, boolean approved) implements Event {
        @Override public String description() { return "ConfirmationResolved(" + tool + "=" + approved + ")"; }
    }

    // ─── Speech output ────────────────────────────────

    public record SpeechSynthesisStarted(String text) implements Event {
        @Override public String description() { return "SpeechSynthesisStarted"; }
    }

    public record SpeechSynthesisCompleted(String text) implements Event {
        @Override public String description() { return "SpeechSynthesisCompleted"; }
    }
}
