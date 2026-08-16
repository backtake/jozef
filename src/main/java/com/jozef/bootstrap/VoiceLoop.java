package com.jozef.bootstrap;

import com.jozef.core.agent.AgentResult;
import com.jozef.core.agent.AgentRuntime;
import com.jozef.core.events.AgentEvents;
import com.jozef.core.events.EventBus;
import com.jozef.speech.SpeechRecognizer;
import com.jozef.speech.SpeechSynthesizer;
import com.jozef.speech.SpeechTextCleaner;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The eternal voice loop. After startup, continuously:
 *
 *   listen → recognize → agent reason → execute tools → speak → listen …
 *
 * Runs on a background thread. Never exits unless interrupted or told to exit.
 */
@Slf4j
public class VoiceLoop implements Runnable {

    private static final Set<String> EXIT_PHRASES =
            Set.of("exit", "quit", "wyjście", "wyjscie", "koniec", "stop");

    private final AgentRuntime agent;
    private final SpeechRecognizer recognizer;
    private final SpeechSynthesizer synthesizer;
    private final EventBus eventBus;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public VoiceLoop(AgentRuntime agent, SpeechRecognizer recognizer, SpeechSynthesizer synthesizer, EventBus eventBus) {
        this.agent = agent;
        this.recognizer = recognizer;
        this.synthesizer = synthesizer;
        this.eventBus = eventBus;
    }

    public void stop() {
        running.set(false);
    }

    @Override
    public void run() {
        log.info("Voice loop started. Listening...");
        printBanner();

        while (running.get()) {
            try {
                eventBus.publish(new AgentEvents.ListeningStarted());
                String input = recognizer.listen();
                if (input == null || input.isBlank()) {
                    continue;
                }
                if (isExitPhrase(input)) {
                    log.info("Exit phrase received: {}", input);
                    running.set(false);
                    break;
                }

                AgentResult result = agent.process(input);
                String text = (result.text() == null || result.text().isBlank())
                        ? "Done."
                        : SpeechTextCleaner.clean(result.text());
                eventBus.publish(new AgentEvents.SpeechSynthesisStarted(text));
                synthesizer.speak(text);
                eventBus.publish(new AgentEvents.SpeechSynthesisCompleted(text));

            } catch (Exception e) {
                log.error("Voice loop iteration failed", e);
                synthesizer.speak("Przepraszam, coś poszło nie tak. Spróbuj ponownie.");
            }
        }

        log.info("Voice loop ended.");
    }

    private boolean isExitPhrase(String input) {
        return EXIT_PHRASES.contains(input.trim().toLowerCase());
    }

    private void printBanner() {
        System.out.println("\n╔══════════════════════════════════════╗");
        System.out.println("║  Jozef is listening.                ║");
        System.out.println("║  Type your command and press Enter. ║");
        System.out.println("║  Type 'exit' to quit.               ║");
        System.out.println("╚══════════════════════════════════════╝\n");
    }
}
