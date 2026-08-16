package com.jozef.proactive;

import com.jozef.core.agent.AgentResult;
import com.jozef.core.agent.AgentRuntime;
import com.jozef.core.events.EventBus;
import com.jozef.speech.SpeechCapability;
import com.jozef.speech.SpeechTextCleaner;
import lombok.extern.slf4j.Slf4j;

/**
 * Injects a proactive message into the agent loop and speaks the answer.
 *
 * Used by timers and watchers (calendar/gmail) so Jozef can act on its own,
 * not only in response to a voice command.
 */
@Slf4j
public class ProactiveAgentService {

    private final AgentRuntime agent;
    private final SpeechCapability speech;
    private final EventBus eventBus;

    public ProactiveAgentService(AgentRuntime agent, SpeechCapability speech, EventBus eventBus) {
        this.agent = agent;
        this.speech = speech;
        this.eventBus = eventBus;
    }

    public void trigger(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        eventBus.publish(new ProactiveEvent(message));
        try {
            AgentResult result = agent.process(message);
            String text = SpeechTextCleaner.clean(result.text());
            if (!text.isBlank()) {
                speech.synthesizer().speak(text);
            }
        } catch (Exception e) {
            log.error("Proactive trigger failed: {}", e.getMessage(), e);
        }
    }
}
