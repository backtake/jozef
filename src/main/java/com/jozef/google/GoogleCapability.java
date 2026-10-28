package com.jozef.google;

import com.jozef.configuration.GoogleProperties;
import com.jozef.core.action.Action;
import com.jozef.core.action.ActionContext;
import com.jozef.core.action.ActionResult;
import com.jozef.core.action.ExecutionStrategy;
import com.jozef.core.action.LambdaAction;
import com.jozef.core.capabilities.Capability;
import com.jozef.core.tool.ToolParameters;
import com.jozef.core.tool.ToolRegistry;
import com.jozef.google.auth.GoogleAuthService;
import com.jozef.google.auth.GoogleCredentialsFile;
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

        if (isBlank(clientId) || isBlank(clientSecret)) {
            log.warn("Google capability skipped — brak danych logowania. Aby włączyć kalendarz i Gmaila:\n"
                    + "  1. Wejdź na https://console.cloud.google.com/apis/credentials\n"
                    + "  2. Utwórz projekt i dane logowania 'OAuth client ID' typu 'Desktop app'\n"
                    + "  3. Pobierz plik JSON i zapisz go jako: " + props.credentialsFile() + "\n"
                    + "     (albo ustaw zmienne środowiskowe GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET)\n"
                    + "  4. Uruchom Jozefa ponownie — otworzy okno logowania Google.");
            return;
        }

        this.auth = new GoogleAuthService(clientId, clientSecret, new GoogleTokenStore(props.tokensFile()));
        this.calendar = new GoogleCalendarService(auth, props.calendarId());
        this.gmail = new GmailService(auth);

        registerCalendarTools();
        registerGmailTools();

        if (props.authenticateOnStartup()) {
            Thread authThread = new Thread(() -> {
                try {
                    auth.accessToken();
                    log.info("Google authentication ready.");
                } catch (Exception e) {
                    log.error("Google startup authentication failed: {}", e.getMessage(), e);
                }
            }, "google-startup-auth");
            authThread.setDaemon(true);
            authThread.start();
        }

        log.info("Google capability initialized (calendar + gmail, {} tools total).", toolRegistry.size());
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
                Set.of("GOOGLE_CALENDAR"), ExecutionStrategy.SAFE,
                ctx -> {
                    CalendarEvent event = calendar.createEvent(
                            require(ctx, "summary"),
                            require(ctx, "start"),
                            require(ctx, "end"),
                            ctx.getString("description"),
                            ctx.getString("location"));
                    return ActionResult.ok("Created event: " + event.summary() + " (" + event.htmlLink() + ")",
                            event);
                }));

        toolRegistry.register(action("calendar_delete_event",
                "Delete an event from Google Calendar by its id.",
                new ToolParameters().string("event_id", "ID of the event to delete", true).build(),
                Set.of("GOOGLE_CALENDAR"), ExecutionStrategy.CONFIRM,
                ctx -> {
                    String id = require(ctx, "event_id");
                    calendar.deleteEvent(id);
                    return ActionResult.ok("Deleted event " + id);
                }));
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

    private Action action(String name, String description, Map<String, Object> schema,
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
