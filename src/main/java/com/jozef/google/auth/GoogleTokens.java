package com.jozef.google.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * OAuth tokens received from (and refreshed against) Google.
 *
 * @param accessToken     short-lived bearer token for API calls
 * @param refreshToken    long-lived token used to obtain a new access token
 * @param expiresAtMillis epoch millis when the access token expires
 * @param scope           granted OAuth scopes (informational)
 */
public record GoogleTokens(
        String accessToken,
        String refreshToken,
        long expiresAtMillis,
        String scope
) {
    /** Treat the token as expired 60s early to avoid edge-of-expiry failures. */
    @JsonIgnore
    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAtMillis - 60_000L;
    }
}
