package com.jozef.core.events;

/**
 * Events that are part of the core runtime lifecycle.
 */
public final class CoreEvents {

    private CoreEvents() {}

    public record ApplicationStarted(String version) implements Event {
        @Override public String description() { return "ApplicationStarted(v=" + version + ")"; }
    }

    public record ApplicationStopping() implements Event {
        @Override public String description() { return "ApplicationStopping"; }
    }

    public record CapabilityInitialized(String capabilityName) implements Event {
        @Override public String description() { return "CapabilityInitialized(" + capabilityName + ")"; }
    }

    public record CapabilityShutdown(String capabilityName) implements Event {
        @Override public String description() { return "CapabilityShutdown(" + capabilityName + ")"; }
    }

    public record CapabilityFailed(String capabilityName, String error) implements Event {
        @Override public String description() { return "CapabilityFailed(" + capabilityName + ")"; }
    }

    public record HealthCheckPassed() implements Event {
        @Override public String description() { return "HealthCheckPassed"; }
    }

    public record HealthCheckFailed(String component, String reason) implements Event {
        @Override public String description() { return "HealthCheckFailed(" + component + ")"; }
    }

    public record HotkeyPressed() implements Event {
        @Override public String description() { return "HotkeyPressed"; }
    }

    /** Something in the desktop UI (tray menu) asks the runtime to do something. */
    public record UiRequest(Kind kind) implements Event {
        public enum Kind { OPEN_COMMAND_WINDOW, TOGGLE_LISTENING, OPEN_SHORTCUTS_FILE, CONNECT_GOOGLE }
        @Override public String description() { return "UiRequest(" + kind + ")"; }
    }

    /** Listening was paused or resumed (so the tray/overlay can show it). */
    public record ListeningStateChanged(boolean paused) implements Event {
        @Override public String description() { return "ListeningStateChanged(paused=" + paused + ")"; }
    }
}
