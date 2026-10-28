package com.jozef.core.session;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jozef.core.util.AtomicFiles;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages session lifecycle and persists conversation history so Jozef keeps
 * the context across restarts.
 *
 * <p>The conversation is saved to a JSON file (default {@code data/session.json})
 * after each turn. On startup the last session is restored; if it cannot be
 * loaded, a fresh session is created.</p>
 */
@Slf4j
public class SessionManager {

    private static final String DEFAULT_FILE = "data/session.json";

    private final Path sessionFile;
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile Session currentSession;

    public SessionManager() {
        this(DEFAULT_FILE);
    }

    public SessionManager(String sessionFile) {
        this.sessionFile = Path.of(sessionFile == null || sessionFile.isBlank() ? DEFAULT_FILE : sessionFile);
        this.currentSession = load();
        if (this.currentSession == null) {
            this.currentSession = new Session();
            log.info("Session created: {}", currentSession.id());
        } else {
            log.info("Session restored: {} ({} turns)", currentSession.id(), currentSession.conversation().size());
        }
    }

    public Session current() {
        return currentSession;
    }

    public Session reset() {
        var oldId = currentSession.id();
        this.currentSession = new Session();
        persist();
        log.info("Session reset: {} → {}", oldId, currentSession.id());
        return currentSession;
    }

    /** Writes the current conversation to disk. Safe to call after each turn. */
    public void persist() {
        try {
            Files.createDirectories(sessionFile.toAbsolutePath().getParent());

            Map<String, Object> root = new LinkedHashMap<>();
            root.put("id", currentSession.id());
            root.put("startedAtMillis", currentSession.startedAt().toEpochMilli());

            List<Map<String, Object>> turns = new ArrayList<>();
            for (Session.ConversationTurn turn : currentSession.conversation()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("role", turn.role());
                m.put("content", turn.content());
                m.put("timestampMillis", turn.timestamp().toEpochMilli());
                turns.add(m);
            }
            root.put("turns", turns);

            AtomicFiles.write(sessionFile, root, mapper);
        } catch (Exception e) {
            log.error("Could not persist session to {}: {}", sessionFile, e.getMessage(), e);
        }
    }

    private Session load() {
        if (!Files.exists(sessionFile)) {
            return null;
        }
        try {
            Map<String, Object> root = mapper.readValue(sessionFile.toFile(), new TypeReference<>() {});

            String id = root.get("id") instanceof String s ? s : UUID.randomUUID().toString();
            long startedAtMillis = root.get("startedAtMillis") instanceof Number n
                    ? n.longValue() : System.currentTimeMillis();
            Session session = new Session(id, Instant.ofEpochMilli(startedAtMillis));

            Object rawTurns = root.get("turns");
            if (rawTurns instanceof List<?> list) {
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> m)) {
                        continue;
                    }
                    String role = m.get("role") == null ? "user" : String.valueOf(m.get("role"));
                    String content = m.get("content") == null ? "" : String.valueOf(m.get("content"));
                    long ts = m.get("timestampMillis") instanceof Number n
                            ? n.longValue() : System.currentTimeMillis();
                    session.addTurn(new Session.ConversationTurn(role, content, Instant.ofEpochMilli(ts)));
                }
            }
            return session;
        } catch (Exception e) {
            log.warn("Could not load session from {}: {}", sessionFile, e.getMessage());
            return null;
        }
    }
}
