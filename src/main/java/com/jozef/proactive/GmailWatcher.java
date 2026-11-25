package com.jozef.proactive;

import com.jozef.google.gmail.GmailMessage;
import com.jozef.google.gmail.GmailService;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Polls Gmail for new unread messages and proactively announces them.
 * Each message id is announced only once per run.
 */
@Slf4j
public class GmailWatcher {

    private final GmailService gmail;
    private final ProactiveAgentService agent;
    private final int maxResults;
    private final Set<String> notified = ConcurrentHashMap.newKeySet();

    public GmailWatcher(GmailService gmail, ProactiveAgentService agent, int maxResults) {
        this.gmail = gmail;
        this.agent = agent;
        this.maxResults = maxResults;
    }

    public void start(ScheduledExecutorService executor, Duration pollInterval) {
        long millis = pollInterval.toMillis();
        executor.scheduleWithFixedDelay(this::check, millis, millis, TimeUnit.MILLISECONDS);
    }

    void check() {
        try {
            for (GmailMessage message : gmail.listRecent(maxResults, "is:unread")) {
                if (message.id() != null && notified.add(message.id())) {
                    agent.trigger("Masz nowego maila od " + message.from() + ". Temat: " + message.subject() + ".");
                }
            }
        } catch (Exception e) {
            log.warn("Gmail check failed: {}", e.getMessage());
        }
    }
}
