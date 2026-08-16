package com.jozef.overlay;

import com.jozef.configuration.OverlayProperties;
import com.jozef.core.capabilities.Capability;
import com.jozef.core.events.AgentEvents;
import com.jozef.core.events.Event;
import com.jozef.core.events.EventBus;
import lombok.extern.slf4j.Slf4j;

import java.awt.GraphicsEnvironment;

/**
 * Overlay capability: shows a small always-on-top HUD reflecting Jozef's state.
 *
 * It subscribes to {@link Event} and maps lifecycle/agent events to
 * {@link OverlayState}. Skipped gracefully in headless environments.
 */
@Slf4j
public class OverlayCapability implements Capability {

    private final OverlayProperties props;
    private final EventBus eventBus;
    private Overlay overlay;

    public OverlayCapability(OverlayProperties props, EventBus eventBus) {
        this.props = props;
        this.eventBus = eventBus;
    }

    public Overlay overlay() {
        return overlay;
    }

    @Override
    public String name() {
        return "overlay";
    }

    @Override
    public int priority() {
        return 60;
    }

    @Override
    public void initialize() {
        if (!props.enabled()) {
            log.info("Overlay capability disabled by configuration.");
            return;
        }
        if (GraphicsEnvironment.isHeadless()) {
            log.warn("Overlay capability skipped: headless environment.");
            return;
        }

        this.overlay = new SwingOverlay(props);
        eventBus.register(Event.class, event -> {
            OverlayState state = stateFor(event);
            if (state != null) {
                overlay.show(state, detailFor(event));
            }
        });
        log.info("Overlay capability initialized (position={}).", props.position());
    }

    /**
     * Maps an event to the overlay state it should show, or {@code null} when
     * the event does not change the overlay.
     */
    static OverlayState stateFor(Event event) {
        if (event instanceof AgentEvents.ListeningStarted) {
            return OverlayState.LISTENING;
        }
        if (event instanceof AgentEvents.UserCommandReceived) {
            return OverlayState.THINKING;
        }
        if (event instanceof AgentEvents.ConfirmationRequested) {
            return OverlayState.CONFIRM;
        }
        if (event instanceof AgentEvents.SpeechSynthesisStarted) {
            return OverlayState.SPEAKING;
        }
        if (event instanceof AgentEvents.SpeechSynthesisCompleted
                || event instanceof AgentEvents.LLMRequestFailed) {
            return OverlayState.IDLE;
        }
        return null;
    }

    static String detailFor(Event event) {
        if (event instanceof AgentEvents.ActionStarted a) {
            return a.tool();
        }
        if (event instanceof AgentEvents.ConfirmationRequested c) {
            return c.tool();
        }
        return null;
    }

    @Override
    public void shutdown() {
        if (overlay != null) {
            overlay.hide();
        }
        log.info("Overlay capability shut down");
    }
}
