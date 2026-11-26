package com.jozef.google.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;

import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Google OAuth 2.0 for installed (desktop) applications — RFC 8252 loopback flow.
 *
 * <ol>
 *   <li>Start a tiny local HTTP server on a free loopback port.</li>
 *   <li>Open the Google consent screen in the default browser.</li>
 *   <li>Google redirects to {@code http://localhost:PORT/callback?code=…}.</li>
 *   <li>Exchange the authorization code for access + refresh tokens.</li>
 *   <li>Persist the tokens; refresh them transparently when they expire.</li>
 * </ol>
 *
 * The rest of the app only ever calls {@link #accessToken()} — authentication is
 * fully lazy: it happens on the first Calendar/Gmail tool call, not at startup.
 */
@Slf4j
public class GoogleAuthService {

    public static final String SCOPE_CALENDAR = "https://www.googleapis.com/auth/calendar";
    public static final String SCOPE_GMAIL_SEND = "https://www.googleapis.com/auth/gmail.send";
    public static final String SCOPE_GMAIL_READONLY = "https://www.googleapis.com/auth/gmail.readonly";

    private static final String AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final long AUTH_TIMEOUT_SECONDS = 300;

    private final String clientId;
    private final String clientSecret;
    private final GoogleTokenStore tokenStore;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    private volatile GoogleTokens tokens;

    public GoogleAuthService(String clientId, String clientSecret, GoogleTokenStore tokenStore) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tokenStore = tokenStore;
        this.httpClient = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
    }

    private volatile Thread signInThread;

    /**
     * Returns a valid access token, refreshing it when expired (a quick network call).
     * Never waits for the user: without tokens it throws {@link GoogleNotSignedInException}
     * — call {@link #startSignIn()} from a user action to open the consent screen.
     */
    public synchronized String accessToken() {
        if (tokens == null) {
            tokens = tokenStore.load();
        }
        if (tokens == null) {
            throw new GoogleNotSignedInException();
        }
        if (tokens.isExpired()) {
            try {
                tokens = refreshAccessToken(tokens);
            } catch (TokensRevokedException e) {
                log.warn("{} — forgetting Google tokens; the next Gmail/Calendar request opens sign-in.", e.getMessage());
                tokens = null;
                tokenStore.delete();
                throw new GoogleNotSignedInException();
            }
            tokenStore.save(tokens);
        }
        return tokens.accessToken();
    }

    /** Checks that Google really answers (refreshes if needed) — not just that a token file exists. */
    public boolean verifySignedIn() {
        try {
            accessToken();
            return true;
        } catch (GoogleNotSignedInException e) {
            return false;
        }
    }

    /** True when tokens exist (signed in at least once and not signed out). */
    public synchronized boolean isSignedIn() {
        if (tokens == null) {
            tokens = tokenStore.load();
        }
        return tokens != null;
    }

    /**
     * Opens the Google consent screen and completes sign-in in the background. At most one
     * flow at a time; no lock is held while waiting for the user, so Jozef keeps working.
     *
     * @return true when a new sign-in was started, false when one is already running or done
     */
    public synchronized boolean startSignIn() {
        if (isSignedIn() || (signInThread != null && signInThread.isAlive())) {
            return false;
        }
        signInThread = new Thread(() -> {
            try {
                GoogleTokens fresh = runAuthorizationFlow();
                synchronized (GoogleAuthService.this) {
                    tokens = fresh;
                    tokenStore.save(fresh);
                }
                log.info("Google sign-in completed.");
            } catch (Exception e) {
                log.warn("Google sign-in did not complete: {}", e.getMessage());
            }
        }, "jozef-google-signin");
        signInThread.setDaemon(true);
        signInThread.start();
        return true;
    }

    /**
     * Builds an HTTP request pre-configured with the bearer token.
     * Calling this is what triggers lazy authentication.
     */
    public HttpRequest.Builder authorizedRequest(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken());
    }

    // ─── OAuth flow ─────────────────────────────────────────────

    private GoogleTokens runAuthorizationFlow() {
        int port = findFreePort();
        String redirectUri = "http://localhost:" + port + "/callback";

        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> codeRef = new AtomicReference<>();
        AtomicReference<String> errorRef = new AtomicReference<>();

        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start the OAuth callback server", e);
        }

        server.createContext("/callback", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            Map<String, String> params = parseQuery(query);
            if (params.containsKey("code")) {
                codeRef.set(params.get("code"));
            } else {
                errorRef.set(params.getOrDefault("error", "user_cancelled"));
            }

            String html = "<html><head><meta charset=\"utf-8\"></head><body>"
                    + "<h3>Jozef — zalogowano do Google. Możesz zamknąć to okno.</h3>"
                    + "</body></html>";
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
            done.countDown();
        });

        server.start();
        try {
            String authUrl = buildAuthUrl(redirectUri);
            log.info("Google sign-in required. Opening: {}", authUrl);
            openBrowser(authUrl);

            if (!done.await(AUTH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Google sign-in timed out after " + AUTH_TIMEOUT_SECONDS + "s");
            }
            if (codeRef.get() == null) {
                throw new IllegalStateException("Google sign-in failed: " + errorRef.get());
            }
            return exchangeCode(codeRef.get(), redirectUri);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Google sign-in interrupted", e);
        } finally {
            server.stop(0);
        }
    }

    private String buildAuthUrl(String redirectUri) {
        String scope = String.join(" ", SCOPE_CALENDAR, SCOPE_GMAIL_SEND, SCOPE_GMAIL_READONLY);
        return AUTH_ENDPOINT + "?"
                + "client_id=" + urlEncode(clientId)
                + "&redirect_uri=" + urlEncode(redirectUri)
                + "&response_type=code"
                + "&scope=" + urlEncode(scope)
                + "&access_type=offline"
                + "&prompt=consent";
    }

    private void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Exception e) {
            log.warn("Could not open the browser automatically: {}", e.getMessage());
        }
        log.info("Otwórz ręcznie ten adres i zaloguj się:\n{}", url);
    }

    private GoogleTokens exchangeCode(String code, String redirectUri) {
        String body = "code=" + urlEncode(code)
                + "&client_id=" + urlEncode(clientId)
                + "&client_secret=" + urlEncode(clientSecret)
                + "&redirect_uri=" + urlEncode(redirectUri)
                + "&grant_type=authorization_code";
        return requestTokens(body);
    }

    /** The stored refresh token was rejected — tokens must be forgotten and the user must sign in again. */
    static final class TokensRevokedException extends IllegalStateException {
        TokensRevokedException(String message) {
            super(message);
        }
    }

    private GoogleTokens refreshAccessToken(GoogleTokens current) {
        if (current.refreshToken() == null || current.refreshToken().isBlank()) {
            // Never run the interactive flow here (we may be on a watcher or the agent thread).
            throw new TokensRevokedException("No refresh token available");
        }
        String body = "refresh_token=" + urlEncode(current.refreshToken())
                + "&client_id=" + urlEncode(clientId)
                + "&client_secret=" + urlEncode(clientSecret)
                + "&grant_type=refresh_token";
        GoogleTokens fresh = requestTokens(body);
        // Google does not always return a new refresh token on refresh.
        return new GoogleTokens(
                fresh.accessToken(),
                fresh.refreshToken() != null && !fresh.refreshToken().isBlank()
                        ? fresh.refreshToken() : current.refreshToken(),
                fresh.expiresAtMillis(),
                fresh.scope() != null ? fresh.scope() : current.scope());
    }

    private GoogleTokens requestTokens(String formBody) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_ENDPOINT))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                String responseBody = response.body() == null ? "" : response.body();
                if (response.statusCode() == 401 || responseBody.contains("invalid_grant")) {
                    // Refresh token expired/revoked (7-day limit for apps in "Testing", a password
                    // change, removed access): forget the tokens so a fresh sign-in can start.
                    throw new TokensRevokedException("Google refresh token no longer valid (" + response.statusCode() + ")");
                }
                throw new IllegalStateException("Google token request failed ("
                        + response.statusCode() + "): " + responseBody);
            }
            Map<String, Object> json = mapper.readValue(response.body(), new TypeReference<>() {});
            String accessToken = (String) json.get("access_token");
            String refreshToken = (String) json.get("refresh_token");
            Number expiresIn = (Number) json.get("expires_in");
            String scope = (String) json.get("scope");
            long expiresAt = System.currentTimeMillis()
                    + (expiresIn == null ? 3600 : expiresIn.longValue()) * 1000L;
            return new GoogleTokens(accessToken, refreshToken, expiresAt, scope);
        } catch (IOException e) {
            throw new IllegalStateException("Google token request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Google token request interrupted", e);
        }
    }

    // ─── Helpers ────────────────────────────────────────────────

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> map = new LinkedHashMap<>();
        if (query == null || query.isBlank()) {
            return map;
        }
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            if (idx < 0) {
                continue;
            }
            String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
            map.put(key, value);
        }
        return map;
    }

    private static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot find a free port", e);
        }
    }
}
