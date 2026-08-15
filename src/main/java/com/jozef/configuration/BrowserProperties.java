package com.jozef.configuration;

/**
 * Browser automation configuration.
 *
 * <pre>
 * jozef:
 *   browser:
 *     enabled: true
 *     headless: false
 *     channel: msedge        # msedge | chrome | null (bundled chromium)
 *     profile-dir: ""        # persistent authenticated profile (optional)
 * </pre>
 */
public record BrowserProperties(
        boolean enabled,
        boolean headless,
        String channel,
        String profileDir
) {}
