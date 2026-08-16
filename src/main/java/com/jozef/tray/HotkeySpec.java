package com.jozef.tray;

/**
 * A parsed global hotkey: a modifier bitmask + a key code.
 * Key codes use the JNativeHook {@code VC_*} constants.
 */
public record HotkeySpec(int modifiers, int keyCode) {}
