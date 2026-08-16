package com.jozef.tray;

/**
 * Registers Jozef to launch at login (Windows: HKCU Run key).
 */
public interface AutostartManager {

    void enable(String appName, String command);

    void disable(String appName);

    boolean isEnabled(String appName);
}
