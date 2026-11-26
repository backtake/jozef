package com.jozef.bootstrap;

import com.jozef.core.agent.AgentResult;
import com.jozef.core.agent.AgentRuntime;
import com.jozef.core.events.AgentEvents;
import com.jozef.core.events.EventBus;
import com.jozef.core.security.ConfirmationReply;
import com.jozef.speech.SpeechRecognizer;
import com.jozef.speech.SpeechSynthesizer;
import com.jozef.speech.SpeechTextCleaner;
import com.jozef.speech.SpeakingMonitor;
import com.jozef.speech.WakeWordDetector;
import com.jozef.speech.vosk.VoskWakeWordDetector;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The eternal voice loop. After startup, continuously:
 *
 *   listen → recognize → agent reason → execute tools → speak → listen …
 *
 * Runs on a background thread. Never exits unless interrupted or told to exit.
 * Listening can be paused (privacy): the microphone is then released, not just ignored.
 */
@Slf4j
public class VoiceLoop implements Runnable {

    /**
     * Typed-only escape hatch for the console. Everyday words such as "stop" or
     * "koniec" must never shut the assistant down (they also answer "Mam to zrobić?");
     * a background Jozef is closed from the tray.
     */
    private static final Set<String> EXIT_PHRASES = Set.of("exit", "quit");
    private static final long QUICK_EMPTY_MILLIS = 1_000;
    private static final long MAX_BACKOFF_MILLIS = 30_000;
    private static final long ANSWER_WINDOW_MILLIS = 20_000;
    private static final long MAX_SPEECH_WAIT_MILLIS = 120_000;

    private final AgentRuntime agent;
    private final SpeechRecognizer recognizer;
    private final SpeechSynthesizer synthesizer;
    private final EventBus eventBus;
    private final WakeWordDetector wakeWordDetector;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final Object pauseLock = new Object();
    private final SpeakingMonitor speakingMonitor;
    private final String wakeWord;
    private boolean remindedYesNo;

    public VoiceLoop(AgentRuntime agent, SpeechRecognizer recognizer, SpeechSynthesizer synthesizer,
                     EventBus eventBus, WakeWordDetector wakeWordDetector) {
        this(agent, recognizer, synthesizer, eventBus, wakeWordDetector, null, "hej jozef");
    }

    public VoiceLoop(AgentRuntime agent, SpeechRecognizer recognizer, SpeechSynthesizer synthesizer,
                     EventBus eventBus, WakeWordDetector wakeWordDetector,
                     SpeakingMonitor speakingMonitor, String wakeWord) {
        this.agent = agent;
        this.recognizer = recognizer;
        this.synthesizer = synthesizer;
        this.eventBus = eventBus;
        this.wakeWordDetector = wakeWordDetector;
        this.speakingMonitor = speakingMonitor;
        this.wakeWord = wakeWord == null || wakeWord.isBlank() ? "hej jozef" : wakeWord;
        if (speakingMonitor != null) {
            // When Jozef starts talking (e.g. a reminder) stop the current listen; the loop
            // waits for silence and listens afresh, so its own voice never becomes input.
            speakingMonitor.addSpeechStartListener(this::interruptListening);
        }
    }

    public void stop() {
        running.set(false);
        setPaused(false);
        interruptListening();
    }

    /** Pause/resume listening. While paused the microphone is closed. */
    public void setPaused(boolean pause) {
        synchronized (pauseLock) {
            // Set the flag and interrupt under the same lock the loop holds while it
            // re-arms listening — a pause can never slip between the check and the listen.
            paused.set(pause);
            if (pause) {
                interruptListening();
            } else {
                pauseLock.notifyAll();
            }
        }
        log.info(pause ? "Listening paused — microphone released." : "Listening resumed.");
    }

    /** Flips pause state atomically; returns the new state (true = paused). */
    public boolean togglePaused() {
        synchronized (pauseLock) {
            boolean next = !paused.get();
            setPaused(next);
            return next;
        }
    }

    public boolean isPaused() {
        return paused.get();
    }

    private void interruptListening() {
        if (wakeWordDetector != null) wakeWordDetector.interrupt();
        recognizer.interrupt();
    }

    /** Re-arms listening unless paused. Returns false when the loop should not listen now. */
    private boolean armListening() {
        synchronized (pauseLock) {
            if (paused.get() || !running.get()) return false;
            if (wakeWordDetector != null) wakeWordDetector.clearInterrupt();
            recognizer.clearInterrupt();
            return true;
        }
    }

    @Override
    public void run() {
        log.info("Voice loop started. Listening...");
        printBanner();
        int quickEmpty = 0;
        remindedYesNo = false;

        while (running.get()) {
            try {
                waitWhilePaused();
                if (!running.get()) break;
                if (speakingMonitor != null) {
                    speakingMonitor.awaitSilence(MAX_SPEECH_WAIT_MILLIS);
                }
                if (!armListening()) continue;
                eventBus.publish(new AgentEvents.ListeningStarted());
                long started = System.currentTimeMillis();

                String input;
                // Without the wake word only right after Jozef asked; later a "tak" from the TV
                // or another person must not approve anything — then "hej Józef, tak" is needed.
                boolean freshQuestion = speakingMonitor == null
                        || started - speakingMonitor.lastSpokeUntil() < ANSWER_WINDOW_MILLIS;
                boolean awaitingAnswer = wakeWordDetector != null && agent.hasPendingConfirmation(AgentRuntime.LOCAL_CHANNEL) && freshQuestion;
                if (wakeWordDetector != null && !awaitingAnswer) {
                    input = wakeWordDetector.awaitWakeWord();
                    if (input != null && input.isBlank() && !paused.get()) {
                        // User said only the wake word — now listen for the command.
                        input = recognizer.listen();
                    }
                } else {
                    input = recognizer.listen();
                }

                if (input == null || input.isBlank()) {
                    // A broken microphone or a closed console returns instantly — back off
                    // instead of spinning a CPU core and flooding the log.
                    quickEmpty = System.currentTimeMillis() - started < QUICK_EMPTY_MILLIS ? quickEmpty + 1 : 0;
                    if (quickEmpty >= 3) {
                        long backoff = Math.min(MAX_BACKOFF_MILLIS, 500L << Math.min(quickEmpty - 3, 6));
                        if (quickEmpty == 3) log.warn("Voice input returns nothing — retrying with back-off.");
                        Thread.sleep(backoff);
                    }
                    continue;
                }
                quickEmpty = 0;
                if (paused.get()) {
                    continue;
                }
                // Never treat Jozef's own voice (a notice read aloud meanwhile) as the user.
                if (speakingMonitor != null && speakingMonitor.spokeDuring(started, System.currentTimeMillis())) {
                    log.debug("Discarding input captured while Jozef was speaking: {}", input);
                    continue;
                }
                // Jozef just asked "Mam to zrobić?" — the answer needs no wake word, but
                // other speech in the room (TV, people) must not become a command. Saying the
                // wake word still works as usual (and drops the pending question).
                if (awaitingAnswer) {
                    String command = VoskWakeWordDetector.commandAfter(input, wakeWord);
                    if (!command.isBlank() || VoskWakeWordDetector.containsWakeWord(input, wakeWord)) {
                        input = command.isBlank() ? input : command;
                    } else if (ConfirmationReply.classify(input, agent.pendingToolNames()) == ConfirmationReply.Kind.OTHER) {
                        log.debug("Ignoring speech while waiting for yes/no: {}", input);
                        if (!remindedYesNo) {
                            remindedYesNo = true;
                            synthesizer.speak("Powiedz tak albo nie.");
                        }
                        continue;
                    }
                }
                remindedYesNo = false;
                if (isExitPhrase(input)) {
                    log.info("Exit phrase received: {}", input);
                    running.set(false);
                    break;
                }

                AgentResult result = agent.process(input);
                String text = (result.text() == null || result.text().isBlank())
                        ? "Gotowe."
                        : SpeechTextCleaner.clean(result.text());
                eventBus.publish(new AgentEvents.SpeechSynthesisStarted(text));
                synthesizer.speak(text);
                eventBus.publish(new AgentEvents.SpeechSynthesisCompleted(text));

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Voice loop iteration failed", e);
                try {
                    synthesizer.speak("Przepraszam, coś poszło nie tak. Spróbuj ponownie.");
                } catch (Exception ignored) {
                    // nothing more we can do
                }
            }
        }

        log.info("Voice loop ended.");
    }

    private void waitWhilePaused() throws InterruptedException {
        synchronized (pauseLock) {
            while (paused.get() && running.get()) {
                pauseLock.wait(1_000);
            }
        }
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
