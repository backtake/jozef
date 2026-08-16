package com.jozef.bootstrap;

import com.jozef.core.agent.AgentRuntime;
import com.jozef.core.events.EventBus;
import com.jozef.core.health.HealthMonitor;
import com.jozef.core.lifecycle.ApplicationLifecycle;
import com.jozef.speech.SpeechCapability;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Triggers the Jozef lifecycle after the Spring context is ready.
 * Starts the runtime, health monitor, and the voice loop; registers a shutdown hook.
 */
@Slf4j
@Component
public class JozefBootstrapRunner implements CommandLineRunner {

    private final ApplicationLifecycle lifecycle;
    private final HealthMonitor healthMonitor;
    private final AgentRuntime agent;
    private final SpeechCapability speech;
    private final EventBus eventBus;

    public JozefBootstrapRunner(ApplicationLifecycle lifecycle,
                                HealthMonitor healthMonitor,
                                AgentRuntime agent,
                                SpeechCapability speech,
                                EventBus eventBus) {
        this.lifecycle = lifecycle;
        this.healthMonitor = healthMonitor;
        this.agent = agent;
        this.speech = speech;
        this.eventBus = eventBus;
    }

    @Override
    public void run(String... args) {
        // Start the runtime (initializes all capabilities)
        lifecycle.start();

        // Start health monitoring
        healthMonitor.start();

        // Start the voice loop on a background thread
        VoiceLoop voiceLoop = new VoiceLoop(agent, speech.recognizer(), speech.synthesizer(), eventBus, speech.wakeWordDetector());
        Thread voiceThread = new Thread(voiceLoop, "jozef-voice-loop");
        voiceThread.setDaemon(false); // keep app alive
        voiceThread.start();

        // Register graceful shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown hook triggered.");
            voiceLoop.stop();
            lifecycle.stop();
        }, "jozef-shutdown"));

        log.info("Jozef runtime is active. Voice loop running.");
    }
}
