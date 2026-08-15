package com.jozef.core.session;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the current session state:
 * - conversation history
 * - active context
 * - user preferences
 * - last actions taken
 * - which plugin is currently active
 */
public class Session {

    private final String id;
    private final Instant startedAt;
    private final List<ConversationTurn> conversation;
    private final Map<String, Object> context;
    private final List<String> lastActions;
    private String activePlugin;
    private volatile boolean listening;

    public Session() {
        this.id = UUID.randomUUID().toString();
        this.startedAt = Instant.now();
        this.conversation = Collections.synchronizedList(new ArrayList<>());
        this.context = new ConcurrentHashMap<>();
        this.lastActions = Collections.synchronizedList(new ArrayList<>());
        this.listening = false;
    }

    public String id() { return id; }
    public Instant startedAt() { return startedAt; }

    public void addTurn(String role, String content) {
        conversation.add(new ConversationTurn(role, content, Instant.now()));
    }

    public List<ConversationTurn> conversation() {
        return List.copyOf(conversation);
    }

    public void setContext(String key, Object value) { context.put(key, value); }
    public Optional<Object> getContext(String key) { return Optional.ofNullable(context.get(key)); }
    public Map<String, Object> context() { return Map.copyOf(context); }

    public void addAction(String actionDescription) {
        lastActions.add(actionDescription);
        if (lastActions.size() > 20) lastActions.remove(0);
    }

    public List<String> lastActions() { return List.copyOf(lastActions); }

    public void setActivePlugin(String plugin) { this.activePlugin = plugin; }
    public Optional<String> activePlugin() { return Optional.ofNullable(activePlugin); }

    public boolean isListening() { return listening; }
    public void setListening(boolean listening) { this.listening = listening; }

    public record ConversationTurn(String role, String content, Instant timestamp) {}
}
