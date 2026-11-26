package com.jozef.bootstrap;

import com.jozef.core.agent.AgentRuntime;
import com.jozef.core.events.CoreEvents;
import com.jozef.core.events.EventBus;
import com.jozef.core.health.HealthMonitor;
import com.jozef.core.lifecycle.ApplicationLifecycle;
import com.jozef.desktop.CommandWindow;
import com.jozef.shortcuts.ShortcutStore;
import com.jozef.speech.SpeechCapability;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Triggers the Jozef lifecycle after the Spring context is ready.
 * Starts the runtime, health monitor, the voice loop and the desktop command
 * window; wires tray/hotkey requests; registers a shutdown hook.
 */
@Slf4j
@Component
public class JozefBootstrapRunner implements CommandLineRunner {

    private final ApplicationLifecycle lifecycle;
    private final HealthMonitor healthMonitor;
    private final AgentRuntime agent;
    private final SpeechCapability speech;
    private final EventBus eventBus;
    private final ShortcutStore shortcuts;
    private final com.jozef.google.GoogleCapability google;
    private final String wakeWord;

    public JozefBootstrapRunner(ApplicationLifecycle lifecycle,
                                HealthMonitor healthMonitor,
                                AgentRuntime agent,
                                SpeechCapability speech,
                                EventBus eventBus,
                                ShortcutStore shortcuts,
                                com.jozef.google.GoogleCapability google,
                                @Value("${jozef.wakeword.word:hej jozef}") String wakeWord) {
        this.wakeWord = wakeWord;
        this.lifecycle = lifecycle;
        this.healthMonitor = healthMonitor;
        this.agent = agent;
        this.speech = speech;
        this.eventBus = eventBus;
        this.shortcuts = shortcuts;
        this.google = google;
    }

    @Override
    public void run(String... args) {
        // Start the runtime (initializes all capabilities)
        lifecycle.start();

        // Start health monitoring
        healthMonitor.start();

        // Start the voice loop on a background thread
        VoiceLoop voiceLoop = new VoiceLoop(agent, speech.recognizer(), speech.synthesizer(), eventBus,
                speech.wakeWordDetector(), speech.speakingMonitor(), wakeWord);
        Thread voiceThread = new Thread(voiceLoop, "jozef-voice-loop");
        voiceThread.setDaemon(false); // keep app alive
        voiceThread.start();

        // Typed commands: global hotkey or tray → command window (works without mic/console).
        CommandWindow commandWindow = CommandWindow.isSupported()
                ? new CommandWindow(agent, speech.synthesizer(), false)
                : null;
        if (commandWindow != null) {
            eventBus.register(CoreEvents.HotkeyPressed.class, e -> commandWindow.toggle());
        }
        eventBus.register(CoreEvents.UiRequest.class, request -> {
            switch (request.kind()) {
                case OPEN_COMMAND_WINDOW -> {
                    if (commandWindow != null) commandWindow.show();
                }
                case TOGGLE_LISTENING -> {
                    voiceLoop.togglePaused();
                    eventBus.publish(new CoreEvents.ListeningStateChanged(voiceLoop.isPaused()));
                }
                case OPEN_SHORTCUTS_FILE -> openInEditor(shortcuts.file().toAbsolutePath().toString());
                case CONNECT_GOOGLE -> google.startSetupInBackground();
            }
        });

        // Register graceful shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown hook triggered.");
            voiceLoop.stop();
            if (commandWindow != null) commandWindow.close();
            lifecycle.stop();
        }, "jozef-shutdown"));

        log.info("Jozef runtime is active. Voice loop running; command window on the global hotkey.");
    }

    private void openInEditor(String path) {
        try {
            new ProcessBuilder("notepad.exe", path).start();
        } catch (Exception e) {
            log.warn("Cannot open {}: {}", path, e.getMessage());
        }
    }
}
