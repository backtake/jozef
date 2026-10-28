package com.jozef.configuration;

/**
 * Google integration configuration (OAuth → Calendar + Gmail).
 *
 * <pre>
 * jozef:
 *   google:
 *     enabled: true
 *     tokens-file: data/google-tokens.json
 *     calendar-id: primary
 *     credentials-file: data/google-credentials.json   # downloaded from Google Cloud Console
 *     authenticate-on-startup: true                    # open browser login on startup
 * </pre>
 *
 * {@code clientId} / {@code clientSecret} are resolved in this order:
 * <ol>
 *   <li>{@code GOOGLE_CLIENT_ID} / {@code GOOGLE_CLIENT_SECRET} environment variables</li>
 *   <li>inline {@code client-id} / {@code client-secret} (local dev only)</li>
 *   <li>{@code credentials-file} (the JSON downloaded from Google Cloud Console)</li>
 * </ol>
 * Credentials are never committed to version control.
 */
public record GoogleProperties(
        boolean enabled,
        String clientId,
        String clientSecret,
        String tokensFile,
        String calendarId,
        String credentialsFile,
        boolean authenticateOnStartup
) {}
