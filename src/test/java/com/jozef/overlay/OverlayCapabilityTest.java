package com.jozef.overlay;

import com.jozef.core.events.AgentEvents;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OverlayCapabilityTest {

    @Test
    void mapsLifecycleEventsToStates() {
        assertThat(OverlayCapability.stateFor(new AgentEvents.ListeningStarted()))
                .isEqualTo(OverlayState.LISTENING);
        assertThat(OverlayCapability.stateFor(new AgentEvents.UserCommandReceived("hi")))
                .isEqualTo(OverlayState.THINKING);
        assertThat(OverlayCapability.stateFor(new AgentEvents.ConfirmationRequested("gmail_send", Map.of(), "CONFIRM")))
                .isEqualTo(OverlayState.CONFIRM);
        assertThat(OverlayCapability.stateFor(new AgentEvents.SpeechSynthesisStarted("text")))
                .isEqualTo(OverlayState.SPEAKING);
        assertThat(OverlayCapability.stateFor(new AgentEvents.SpeechSynthesisCompleted("text")))
                .isEqualTo(OverlayState.IDLE);
    }

    @Test
    void ignoresUnmappedEvents() {
        assertThat(OverlayCapability.stateFor(new AgentEvents.LLMRequestStarted())).isNull();
    }
}
