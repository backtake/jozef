package com.jozef.overlay;

/**
 * A lightweight, always-on-top status HUD showing Jozef's current state.
 * Platform implementations: Swing (Windows), AWT, or none (headless).
 */
public interface Overlay {

    /** Show the overlay in the given state (IDLE hides it). */
    void show(OverlayState state, String detail);

    void hide();

    boolean isVisible();
}
