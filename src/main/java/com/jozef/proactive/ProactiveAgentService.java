package com.jozef.proactive;

import com.jozef.core.agent.AgentRuntime;
import com.jozef.core.events.EventBus;
import com.jozef.speech.SpeechCapability;
import com.jozef.speech.SpeechTextCleaner;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Delivers proactive notices (reminders, upcoming meetings, new mail) to the user.
 *
 * Notices are spoken as-is and recorded in the conversation as something Jozef
 * said. They deliberately do NOT go through the reasoning loop: an e-mail subject
 * or a reminder text is data, not an instruction — routing it to the model as a
 * user message would let any sender steer Jozef (or answer a pending "Mam
 * wysłać?" with "tak").
 *
 * Delivery runs on its own thread, in order, so waiting for a pending question to
 * be answered never delays timers or watchers.
 */
@Slf4j
public class ProactiveAgentService implements AutoCloseable {

    private static final long MAX_DEFER_MILLIS = 4 * 60_000;

    private final AgentRuntime agent;
    private final SpeechCapability speech;
    private final EventBus eventBus;
    private final ExecutorService delivery = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "jozef-notices");
        t.setDaemon(true);
        return t;
    });

    public ProactiveAgentService(AgentRuntime agent, SpeechCapability speech, EventBus eventBus) {
        this.agent = agent;
        this.speech = speech;
        this.eventBus = eventBus;
    }

    /** Queue a notice for the user. Returns immediately. */
    public void trigger(String notice) {
        if (notice == null || notice.isBlank()) {
            return;
        }
        if (eventBus != null) {
            eventBus.publish(new ProactiveEvent(notice));
        }
        delivery.submit(() -> deliver(notice));
    }

    private void deliver(String notice) {
        try {
            // Don't talk over a pending "Mam to zrobić?" — the user's next "tak" belongs to
            // that question, not to whatever a notice says. Wait until it is answered/expired.
            long deadline = System.currentTimeMillis() + MAX_DEFER_MILLIS;
            while (agent.hasPendingConfirmation() && System.currentTimeMillis() < deadline) {
                Thread.sleep(2_000);
            }
            agent.recordAssistantNotice(notice);
            String text = SpeechTextCleaner.clean(notice);
            if (!text.isBlank()) {
                speech.synthesizer().speak(text);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Proactive notice failed: {}", e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        delivery.shutdownNow();
    }
}
