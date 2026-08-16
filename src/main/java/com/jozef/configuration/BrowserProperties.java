package com.jozef.configuration;

/**
 * Browser automation configuration.
 *
 * <pre>
 * jozef:
 *   browser:
 *     enabled: true
 *     headless: false
 *     channel: chrome        # chrome | msedge | null (bundled chromium)
 *     profile-dir: ""        # persistent authenticated profile (optional)
 *     executable-path: ""    # explicit browser binary, e.g. Brave (overrides channel)
 * </pre>
 */
public record BrowserProperties(
        boolean enabled,
        boolean headless,
        String channel,
        String profileDir,
        String executablePath
) {}
