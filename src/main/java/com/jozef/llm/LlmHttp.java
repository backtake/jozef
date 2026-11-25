package com.jozef.llm;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;

/**
 * Small shared HTTP transport for LLM adapters: bounded timeouts and a single
 * place that turns transport failures and non-2xx responses into {@link LlmException}.
 */
public final class LlmHttp {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_ERROR_BODY = 500;

    private final HttpClient client;
    private final Duration requestTimeout;
    private final String providerName;

    public LlmHttp(String providerName, Duration requestTimeout) {
        this(providerName, requestTimeout,
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
    }

    LlmHttp(String providerName, Duration requestTimeout, HttpClient client) {
        this.providerName = providerName;
        this.requestTimeout = requestTimeout;
        this.client = client;
    }

    /** POST a JSON body; returns the response body or throws {@link LlmException}. */
    public String postJson(String url, Map<String, String> headers, String json) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        headers.forEach((k, v) -> {
            if (v != null && !v.isBlank()) builder.header(k, v);
        });

        HttpResponse<String> response;
        try {
            response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new LlmException(providerName + " timed out after " + requestTimeout.toSeconds() + "s", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException(providerName + " request interrupted", e);
        } catch (Exception e) {
            throw new LlmException(providerName + " unreachable: " + e.getMessage(), e);
        }

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            String body = response.body() == null ? "" : response.body();
            if (body.length() > MAX_ERROR_BODY) body = body.substring(0, MAX_ERROR_BODY) + "…";
            throw new LlmException(providerName + " returned HTTP " + status + ": " + body, status, null);
        }
        return response.body();
    }
}
