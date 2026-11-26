package com.jozef.core.security;

import com.jozef.core.action.Action;

import java.util.Set;

/**
 * Which tools bring third-party text into the conversation, and which tools must be
 * confirmed after that happened in the same turn. One place, so a new reading tool
 * cannot quietly open a path for injected instructions to persist or leak data.
 *
 * <ul>
 *   <li>After <b>public</b> third-party text (web pages, search results): further reading
 *       is fine (normal research), but saving memory, opening something in the user's
 *       real browser or an app needs a yes.</li>
 *   <li>After <b>private</b> data (e-mail, calendar): additionally any fetch of an address
 *       (a URL can carry the data out) needs a yes.</li>
 * </ul>
 */
public final class UntrustedContentPolicy {

    public enum Taint { NONE, PUBLIC, PRIVATE }

    private static final Set<String> PUBLIC_SOURCES = Set.of(
            "read_web_page", "web_lookup", "browser_get_page_text", "browser_read_text",
            // stored notes may have been written from third-party text in the past
            "recall_notes", "recall_day_notes");

    private static final Set<String> PRIVATE_SOURCES = Set.of(
            "gmail_list_recent", "gmail_read_message",
            "calendar_list_events", "calendar_events_on_day", "daily_briefing");

    /** Persisting or acting in the user's own session. */
    private static final Set<String> CONFIRM_AFTER_PUBLIC = Set.of(
            "remember_note", "open_url", "open_application", "search_web", "set_timer", "google_connect");

    /** Anything that sends a request to an address the model chose. */
    private static final Set<String> CONFIRM_AFTER_PRIVATE = Set.of(
            "read_web_page", "web_lookup", "browser_navigate", "browser_search");

    private UntrustedContentPolicy() {}

    /** Taint produced by a successful run of {@code action}. */
    public static Taint taintOf(Action action) {
        if (PRIVATE_SOURCES.contains(action.name())) return Taint.PRIVATE;
        if (PUBLIC_SOURCES.contains(action.name()) || action.returnsUntrustedContent()) return Taint.PUBLIC;
        return Taint.NONE;
    }

    /** Whether {@code action} needs confirmation given what was read earlier in the turn. */
    public static boolean needsConfirmation(Action action, Taint taint) {
        if (taint == Taint.NONE) return false;
        boolean afterPublic = action.confirmAfterUntrustedContent() || CONFIRM_AFTER_PUBLIC.contains(action.name());
        if (taint == Taint.PUBLIC) return afterPublic;
        return afterPublic || CONFIRM_AFTER_PRIVATE.contains(action.name());
    }

    public static Taint max(Taint a, Taint b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
