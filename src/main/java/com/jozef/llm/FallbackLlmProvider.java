package com.jozef.llm;

import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Wraps a primary {@link LLMProvider} with an automatic fallback.
 *
 * When the primary provider fails (throws — connection error, timeout, non-2xx
 * response), the same request is retried against the fallback provider. After a
 * failure the primary is skipped for a short cool-down, so a dead local model does
 * not add its full timeout to every step of every turn (voice latency matters).
 */
@Slf4j
public class FallbackLlmProvider implements LLMProvider {

    static final Duration COOL_DOWN = Duration.ofSeconds(60);
    static final Duration ACCOUNT_COOL_DOWN = Duration.ofMinutes(30);

    private final LLMProvider primary;
    private final LLMProvider fallback;
    private final Clock clock;
    private volatile Instant primaryBlockedUntil = Instant.MIN;
    private volatile LlmException lastPrimaryError;

    public FallbackLlmProvider(LLMProvider primary, LLMProvider fallback) {
        this(primary, fallback, Clock.systemUTC());
    }

    FallbackLlmProvider(LLMProvider primary, LLMProvider fallback, Clock clock) {
        if (primary == null || fallback == null) {
            throw new IllegalArgumentException("Primary and fallback providers are required");
        }
        this.primary = primary;
        this.fallback = fallback;
        this.clock = clock;
    }

    @Override
    public String name() {
        return primary.name() + "→" + fallback.name();
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        if (clock.instant().isAfter(primaryBlockedUntil)) {
            try {
                ChatResponse response = primary.chat(request);
                if (response != null) {
                    lastPrimaryError = null;
                    return response;
                }
                log.warn("Primary LLM '{}' returned no response. Falling back to '{}'.", primary.name(), fallback.name());
            } catch (Exception e) {
                if (e instanceof LlmException le && le.statusCode() == 400) {
                    // Usually our own request shape — worth a loud log, it hides real bugs otherwise.
                    log.error("Primary LLM '{}' rejected the request (400): {}", primary.name(), e.getMessage());
                } else {
                    log.warn("Primary LLM '{}' failed ({}). Falling back to '{}' for {}s.",
                            primary.name(), e.getMessage(), fallback.name(), COOL_DOWN.toSeconds());
                }
                if (Thread.currentThread().isInterrupted()) {
                    throw e instanceof RuntimeException re ? re : new LlmException("interrupted", e);
                }
                // An exhausted account / bad key will not heal in a minute — stop paying its latency.
                lastPrimaryError = e instanceof LlmException le0 ? le0 : null;
                boolean accountProblem = e instanceof LlmException le && le.isAccountProblem();
                if (accountProblem) {
                    log.error("Primary LLM '{}' is unusable (account/billing/key): {} — using '{}' for {} min.",
                            primary.name(), e.getMessage(), fallback.name(), ACCOUNT_COOL_DOWN.toMinutes());
                }
                primaryBlockedUntil = clock.instant().plus(accountProblem ? ACCOUNT_COOL_DOWN : COOL_DOWN);
            }
        }
        try {
            return fallback.chat(request);
        } catch (LlmException fallbackError) {
            // Report the primary's reason when it is the actionable one (e.g. no credits).
            LlmException primaryError = lastPrimaryError;
            if (primaryError != null && primaryError.isAccountProblem()) {
                throw new LlmException(primaryError.getMessage() + " | fallback: " + fallbackError.getMessage(),
                        primaryError.statusCode(), fallbackError);
            }
            throw fallbackError;
        }
    }
}
