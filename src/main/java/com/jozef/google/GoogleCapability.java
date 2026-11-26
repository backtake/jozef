package com.jozef.google;

import com.jozef.configuration.GoogleProperties;
import com.jozef.core.action.Action;
import com.jozef.core.action.ActionContext;
import com.jozef.core.action.ActionResult;
import com.jozef.core.action.ExecutionStrategy;
import com.jozef.core.action.LambdaAction;
import com.jozef.core.util.SpokenDates;
import com.jozef.core.capabilities.Capability;
import com.jozef.core.tool.ToolParameters;
import com.jozef.core.tool.ToolRegistry;
import com.jozef.google.auth.GoogleAuthService;
import com.jozef.google.auth.GoogleCredentialsFile;
import com.jozef.google.auth.GoogleCredentialsSetup;
import com.jozef.google.auth.GoogleTokenStore;
import com.jozef.google.calendar.CalendarEvent;
import com.jozef.google.calendar.GoogleCalendarService;
import com.jozef.google.gmail.GmailMessage;
import com.jozef.google.gmail.GmailService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Google capability: OAuth + Calendar + Gmail.
 *
 * Registers the {@code calendar_*} and {@code gmail_*} tools. Authentication is
 * lazy — the consent screen appears only when the first Google tool is used.
 * Sending email and deleting events require confirmation (CONFIRM strategy).
 */
@Slf4j
public class GoogleCapability implements Capability {

    private final GoogleProperties props;
    private final ToolRegistry toolRegistry;

    private GoogleAuthService auth;
    private GoogleCalendarService calendar;
    private GmailService gmail;

    public GoogleCapability(GoogleProperties props, ToolRegistry toolRegistry) {
        this.props = props;
        this.toolRegistry = toolRegistry;
    }

    public GoogleAuthService auth() {
        return auth;
    }

    public GoogleCalendarService calendar() {
        return calendar;
    }

    public GmailService gmail() {
        return gmail;
    }

    @Override
    public String name() {
        return "google";
    }

    @Override
    public int priority() {
        return 50;
    }

    @Override
    public void initialize() {
        if (!props.enabled()) {
            log.info("Google capability disabled by configuration.");
            return;
        }

        String clientId = resolve(props.clientId(), "GOOGLE_CLIENT_ID");
        String clientSecret = resolve(props.clientSecret(), "GOOGLE_CLIENT_SECRET");

        if (isBlank(clientId) || isBlank(clientSecret)) {
            GoogleCredentialsFile creds = GoogleCredentialsFile.load(props.credentialsFile());
            if (creds != null) {
                clientId = creds.clientId();
                clientSecret = creds.clientSecret();
                log.info("Google credentials loaded from {}", props.credentialsFile());
            }
        }

        registerConnectTool();

        if (isBlank(clientId) || isBlank(clientSecret)) {
            // Never block startup and never open a browser on every boot: the guided setup
            // runs in the background once (first run); afterwards only on request
            // ("Józef, połącz się z Google" / tray).
            if (props.authenticateOnStartup() && !setupAlreadyOffered()) {
                markSetupOffered();
                startSetupInBackground();
            } else {
                log.info("Google nie jest połączone — powiedz \"połącz się z Google\", żeby to skonfigurować.");
            }
            return;
        }
        connect(clientId, clientSecret);
    }

    /** Creates the Google services and registers their tools (may run after startup). */
    private synchronized void connect(String clientId, String clientSecret) {
        if (this.auth != null) return;
        this.auth = new GoogleAuthService(clientId, clientSecret, new GoogleTokenStore(props.tokensFile()));
        this.calendar = new GoogleCalendarService(auth, props.calendarId());
        this.gmail = new GmailService(auth);
        registerCalendarTools();
        registerGmailTools();
        // OAuth consent happens lazily on the first Google request — not at boot.
        log.info("Google capability ready (calendar + gmail, {} tools total).", toolRegistry.size());
        for (Runnable listener : connectedListeners) {
            try {
                listener.run();
            } catch (Exception e) {
                log.warn("Google connected-listener failed: {}", e.getMessage());
            }
        }
    }

    private final java.util.List<Runnable> connectedListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Runs {@code listener} once Google is connected — immediately if it already is. */
    public void onConnected(Runnable listener) {
        boolean connectedNow;
        synchronized (this) {
            connectedListeners.add(listener);
            connectedNow = auth != null;
        }
        if (connectedNow) listener.run();
    }

    private volatile Thread setupThread;

    /** Guided first-time setup: open the Cloud Console, wait for the downloaded JSON, connect. */
    public synchronized boolean startSetupInBackground() {
        if (auth != null || (setupThread != null && setupThread.isAlive())) return false;
        setupThread = new Thread(() -> {
            log.info("Konfiguracja Google: w przeglądarce otwiera się Google Cloud Console. Utwórz 'OAuth client ID' "
                    + "typu 'Desktop app' i kliknij 'Pobierz JSON' — Jozef sam znajdzie plik w Pobranych.");
            GoogleCredentialsSetup.openConsoleInBrowser();
            GoogleCredentialsFile found = GoogleCredentialsSetup.findDownloadedCredentials(600_000L);
            if (found == null) {
                log.warn("Nie znaleziono pobranego pliku z danymi Google w ciągu 10 minut.");
                return;
            }
            GoogleCredentialsSetup.save(props.credentialsFile(), found);
            connect(found.clientId(), found.clientSecret());
        }, "jozef-google-setup");
        setupThread.setDaemon(true);
        setupThread.start();
        return true;
    }

    private void registerConnectTool() {
        toolRegistry.register(action("google_connect",
                "Connect Jozef to the user's Google account (Gmail + Calendar). Use when the user asks to set up "
                        + "or connect Google, or a Gmail/Calendar request fails because Google is not connected.",
                null, Set.of("GOOGLE_SETUP"), ExecutionStrategy.SAFE,
                ctx -> {
                    if (auth != null) return ActionResult.ok("Google is already connected.");
                    return startSetupInBackground()
                            ? ActionResult.ok("Google setup started: a browser window opened with Google Cloud Console. "
                            + "Tell the user to create an OAuth client ID of type Desktop app and click Download JSON; "
                            + "Jozef will pick the file up automatically.")
                            : ActionResult.ok("Google setup is already in progress in the browser.");
                }));
    }

    private java.nio.file.Path setupMarker() {
        java.nio.file.Path creds = java.nio.file.Path.of(props.credentialsFile() == null
                ? "data/google-credentials.json" : props.credentialsFile());
        java.nio.file.Path dir = creds.toAbsolutePath().getParent();
        return (dir == null ? java.nio.file.Path.of(".") : dir).resolve("google-setup-offered");
    }

    private boolean setupAlreadyOffered() {
        return java.nio.file.Files.exists(setupMarker());
    }

    private void markSetupOffered() {
        try {
            java.nio.file.Files.createDirectories(setupMarker().getParent());
            java.nio.file.Files.writeString(setupMarker(), java.time.Instant.now().toString());
        } catch (Exception e) {
            log.debug("Cannot write {}: {}", setupMarker(), e.getMessage());
        }
    }

    private void registerCalendarTools() {
        toolRegistry.register(action("calendar_list_events",
                "List upcoming events from Google Calendar.",
                new ToolParameters().integer("max_results", "Maximum number of events (default 10)", false).build(),
                Set.of("GOOGLE_CALENDAR"), ExecutionStrategy.SAFE,
                ctx -> {
                    int max = ctx.get("max_results") instanceof Number n ? n.intValue() : 10;
                    List<CalendarEvent> events = calendar.listUpcoming(max);
                    return ActionResult.ok(formatEvents(events), events);
                }));

        toolRegistry.register(action("calendar_create_event",
                "Create a new event in Google Calendar.",
                new ToolParameters()
                        .string("summary", "Event title", true)
                        .string("start", "Start date/time in ISO 8601, e.g. 2026-08-20T14:00:00", true)
                        .string("end", "End date/time in ISO 8601, e.g. 2026-08-20T14:30:00", true)
                        .string("description", "Event description", false)
                        .string("location", "Event location", false)
                        .build(),
                Set.of("GOOGLE_CALENDAR"), ExecutionStrategy.CONFIRM,
                ctx -> {
                    CalendarEvent event = calendar.createEvent(
                            require(ctx, "summary"),
                            require(ctx, "start"),
                            require(ctx, "end"),
                            ctx.getString("description"),
                            ctx.getString("location"));
                    return ActionResult.ok("Created event: " + event.summary() + " (" + event.htmlLink() + ")",
                            event);
                }).withConfirmationSummary(args -> "dodać do kalendarza „" + args.get("summary") + "” — "
                        + SpokenDates.range(str(args.get("start")), str(args.get("end")))
                        + (args.get("location") == null ? "" : ", miejsce: " + args.get("location"))));

        toolRegistry.register(action("calendar_events_on_day",
                "List the user's calendar events on a given day (\"co mam jutro\", \"plan na piątek\"). "
                        + "Pass the date as yyyy-MM-dd, or 'today' / 'tomorrow'.",
                new ToolParameters().string("date", "Day as yyyy-MM-dd, 'today' or 'tomorrow'", true).build(),
                Set.of("GOOGLE_CALENDAR"), ExecutionStrategy.SAFE,
                ctx -> {
                    String day = com.jozef.core.util.Dates.resolveDay(require(ctx, "date"));
                    if (day == null) return ActionResult.fail("Use a date as yyyy-MM-dd, 'today' or 'tomorrow'");
                    List<CalendarEvent> events = calendar.listForDay(day);
                    return ActionResult.ok(events.isEmpty() ? "No events on " + day + "." : formatEvents(events), events);
                }));

        toolRegistry.register(action("calendar_delete_event",
                "Delete an event from Google Calendar by its id (get the id from a list tool first).",
                new ToolParameters().string("event_id", "ID of the event to delete", true).build(),
                Set.of("GOOGLE_CALENDAR"), ExecutionStrategy.CONFIRM,
                ctx -> {
                    String id = require(ctx, "event_id");
                    calendar.deleteEvent(id);
                    return ActionResult.ok("Deleted event " + id);
                }).withConfirmationSummary(args -> {
                    // Say WHAT gets deleted, not an opaque id.
                    String id = str(args.get("event_id"));
                    try {
                        CalendarEvent e = calendar.getEvent(id);
                        return "usunąć z kalendarza „" + e.summary() + "” (" + SpokenDates.range(e.start(), e.end()) + ")";
                    } catch (Exception ex) {
                        return "usunąć z kalendarza wydarzenie o identyfikatorze " + id + " (nie udało się pobrać jego nazwy)";
                    }
                }));
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private void registerGmailTools() {
        toolRegistry.register(action("gmail_send",
                "Send an email from the authenticated Gmail account.",
                new ToolParameters()
                        .string("to", "Recipient email address", true)
                        .string("subject", "Email subject", true)
                        .string("body", "Plain-text email body", true)
                        .build(),
                Set.of("GOOGLE_GMAIL"), ExecutionStrategy.CONFIRM,
                ctx -> {
                    String id = gmail.sendEmail(
                            require(ctx, "to"),
                            require(ctx, "subject"),
                            require(ctx, "body"));
                    return ActionResult.ok("Email sent (id " + id + ")");
                }).withConfirmationSummary(args -> {
                    String body = str(args.get("body")).replaceAll("\\s+", " ").trim();
                    if (body.length() > 220) body = body.substring(0, 217) + "…";
                    return "wysłać e-mail do " + args.get("to") + ", temat: „" + args.get("subject")
                            + "”, treść: „" + body + "”";
                }));

        toolRegistry.register(action("gmail_reply",
                "Reply to an e-mail in its thread (\"odpowiedz mu, że…\"). Get message_id from gmail_list_recent first.",
                new ToolParameters()
                        .string("message_id", "ID of the message to reply to", true)
                        .string("body", "Plain-text reply", true)
                        .build(),
                Set.of("GOOGLE_GMAIL"), ExecutionStrategy.CONFIRM,
                ctx -> {
                    String id = gmail.replyToMessage(require(ctx, "message_id"), require(ctx, "body"));
                    return ActionResult.ok("Reply sent (id " + id + ")");
                }).withConfirmationSummary(args -> {
                    String body = str(args.get("body")).replaceAll("\\s+", " ").trim();
                    if (body.length() > 220) body = body.substring(0, 217) + "…";
                    try {
                        var target = gmail.replyTarget(str(args.get("message_id")));
                        return "odpowiedzieć na maila „" + target.subject() + "” do " + target.to() + ", treść: „" + body + "”";
                    } catch (Exception e) {
                        return "odpowiedzieć na maila (nie udało się pobrać, do kogo) treścią: „" + body + "”";
                    }
                }));

        toolRegistry.register(action("gmail_list_recent",
                "List recent emails from Gmail.",
                new ToolParameters()
                        .integer("max_results", "Maximum number of messages (default 5)", false)
                        .string("query", "Gmail search query, e.g. \"from:alice newer_than:2d\"", false)
                        .build(),
                Set.of("GOOGLE_GMAIL"), ExecutionStrategy.SAFE,
                ctx -> {
                    int max = ctx.get("max_results") instanceof Number n ? n.intValue() : 5;
                    List<GmailMessage> messages = gmail.listRecent(max, ctx.getString("query"));
                    return ActionResult.ok(formatMessages(messages), messages);
                }));

        toolRegistry.register(action("gmail_read_message",
                "Read a single Gmail message (including its body) by id.",
                new ToolParameters().string("message_id", "ID of the message to read", true).build(),
                Set.of("GOOGLE_GMAIL"), ExecutionStrategy.SAFE,
                ctx -> {
                    GmailMessage message = gmail.readMessage(require(ctx, "message_id"));
                    return ActionResult.ok(formatMessage(message), message);
                }));
    }

    // ─── Helpers ────────────────────────────────────────────────

    private LambdaAction action(String name, String description, Map<String, Object> schema,
                          Set<String> permissions, ExecutionStrategy strategy,
                          Function<ActionContext, ActionResult> executor) {
        return new LambdaAction(name, description, schema, permissions, strategy, executor);
    }

    private static String require(ActionContext ctx, String key) {
        String value = ctx.getString(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required parameter: " + key);
        }
        return value;
    }

    private static String formatEvents(List<CalendarEvent> events) {
        if (events.isEmpty()) {
            return "No upcoming events.";
        }
        StringBuilder sb = new StringBuilder();
        for (CalendarEvent e : events) {
            sb.append("- ").append(e.summary());
            if (e.start() != null) {
                sb.append(" (").append(e.start()).append(")");
            }
            if (e.id() != null) {
                sb.append(" [id=").append(e.id()).append("]");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private static String formatMessages(List<GmailMessage> messages) {
        if (messages.isEmpty()) {
            return "No recent messages.";
        }
        StringBuilder sb = new StringBuilder();
        for (GmailMessage m : messages) {
            sb.append("- [").append(m.id()).append("] ");
            if (m.from() != null) {
                sb.append(m.from()).append(": ");
            }
            sb.append(m.subject() == null ? "(no subject)" : m.subject());
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private static String formatMessage(GmailMessage m) {
        StringBuilder sb = new StringBuilder();
        sb.append("From: ").append(m.from()).append("\n");
        sb.append("Subject: ").append(m.subject()).append("\n");
        sb.append("Snippet: ").append(m.snippet()).append("\n");
        if (m.body() != null) {
            sb.append("Body: ").append(truncate(m.body(), 3000));
        }
        return sb.toString().trim();
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() > max ? text.substring(0, max) + "…" : text;
    }

    private static String resolve(String inline, String envVar) {
        String env = System.getenv(envVar);
        return (env != null && !env.isBlank()) ? env : inline;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Override
    public void shutdown() {
        log.info("Google capability shut down");
        auth = null;
        calendar = null;
        gmail = null;
    }
}
