package com.jozef.configuration;

/**
 * System tray / startup / hotkey configuration.
 *
 * <pre>
 * jozef:
 *   tray:
 *     enabled: true
 *     autostart: false          # register Jozef in Windows startup (HKCU Run key)
 *     hotkey: "ctrl alt J"      # global hotkey; empty string disables it
 * </pre>
 */
public record TrayProperties(
        boolean enabled,
        boolean autostart,
        String hotkey
) {}
