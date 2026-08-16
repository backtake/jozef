package com.jozef.tray;

/**
 * Registers a system-wide hotkey that fires regardless of which window has focus.
 */
public interface GlobalHotkeyService {

    /** Register the hotkey (no-op if already registered). */
    void register();

    /** Unregister the hotkey and release the native hook. */
    void stop();

    boolean isRegistered();
}
