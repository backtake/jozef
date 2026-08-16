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
 * </pre>
 *
 * {@code clientId} / {@code clientSecret} are resolved from the
 * {@code GOOGLE_CLIENT_ID} / {@code GOOGLE_CLIENT_SECRET} environment variables
 * first; the inline fields are a local-dev fallback and must never be committed.
 */
public record GoogleProperties(
        boolean enabled,
        String clientId,
        String clientSecret,
        String tokensFile,
        String calendarId
) {}
