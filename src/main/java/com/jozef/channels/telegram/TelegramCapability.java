package com.jozef.channels.telegram;

import com.jozef.configuration.TelegramProperties;
import com.jozef.core.agent.AgentResult;
import com.jozef.core.agent.AgentRuntime;
import com.jozef.core.capabilities.Capability;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Jozef on the phone: a private Telegram bot. Write to it from anywhere and Jozef
 * acts on the PC and answers — the same conversation as voice and the command window.
 *
 * Security: only chats listed in {@code jozef.telegram.allowed-chat-ids} are served.
 * Anyone else gets their chat id back (so the owner can add themselves) and nothing
 * else. The token lives in an environment variable; the bot connects outwards only.
 */
@Slf4j
public class TelegramCapability implements Capability {

    private final TelegramProperties props;
    private final AgentRuntime agent;
    private final Function<String, String> env;
    private static final long MAX_AGE_SECONDS = 180;
    private static final int MAX_STRANGERS = 100;

    private final Set<Long> answeredStrangers = ConcurrentHashMap.newKeySet();
    private volatile boolean running;
    private Thread poller;
    private TelegramBotApi api;

    public TelegramCapability(TelegramProperties props, AgentRuntime agent) {
        this(props, agent, System::getenv);
    }

    TelegramCapability(TelegramProperties props, AgentRuntime agent, Function<String, String> env) {
        this.props = props;
        this.agent = agent;
        this.env = env;
    }

    @Override
    public String name() {
        return "telegram";
    }

    /** Short Polish status for jozef_status. */
    public String status() {
        if (props == null || !props.enabled()) return "wyłączony";
        if (!running) return "nieaktywny (brak tokenu " + props.tokenEnv() + ")";
        return props.allowedChatIds().isEmpty()
                ? "bot działa, ale nie ma jeszcze dozwolonych czatów"
                : "aktywny (" + props.allowedChatIds().size() + " dozwolony czat)";
    }

    @Override
    public int priority() {
        return 90;
    }

    @Override
    public void initialize() {
        if (props == null || !props.enabled()) {
            log.info("Telegram (phone access) disabled.");
            return;
        }
        String token = env.apply(props.tokenEnv());
        if (token == null || token.isBlank()) {
            log.warn("Telegram enabled but {} is not set — phone access off. Create a bot with @BotFather "
                    + "and put its token in that environment variable.", props.tokenEnv());
            return;
        }
        if (props.allowedChatIds().isEmpty()) {
            log.warn("Telegram: no allowed-chat-ids yet. Write anything to your bot — it will reply with your "
                    + "chat id; add it to jozef.telegram.allowed-chat-ids and restart.");
        }
        start(new TelegramBotApi(token.trim()));
    }

    void start(TelegramBotApi botApi) {
        this.api = botApi;
        running = true;
        poller = new Thread(this::pollLoop, "jozef-telegram");
        poller.setDaemon(true);
        poller.start();
        log.info("Telegram phone access active ({} allowed chat(s)).", props.allowedChatIds().size());
    }

    private void pollLoop() {
        long offset = 0;
        long backoff = 2_000;
        while (running) {
            try {
                List<TelegramBotApi.Update> updates = api.getUpdates(offset);
                backoff = 2_000;
                if (updates.isEmpty()) continue;
                for (TelegramBotApi.Update update : updates) {
                    offset = Math.max(offset, update.updateId() + 1);
                }
                // Acknowledge first: if Jozef dies mid-batch, nothing is re-run on restart
                // (a redelivered "wyślij…" + "tak" would send twice).
                api.acknowledge(offset);
                for (TelegramBotApi.Update update : updates) {
                    handle(update);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                if (!running) return;
                // Never log the exception's URI — it would contain the bot token.
                log.warn("Telegram polling failed ({}); retrying in {} s", e.getClass().getSimpleName(), backoff / 1000);
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
                backoff = Math.min(backoff * 2, 60_000);
            }
        }
    }

    void handle(TelegramBotApi.Update update) {
        if (update.text() == null || update.text().isBlank()) {
            return;
        }
        long chat = update.chatId();
        try {
            if (!props.allowedChatIds().contains(chat)) {
                log.warn("Telegram message from unknown chat {} ({}) ignored.", chat, update.fromName());
                if (answeredStrangers.size() < MAX_STRANGERS && answeredStrangers.add(chat)) {
                    api.sendMessage(chat, "To jest prywatny asystent. Twój identyfikator czatu: " + chat
                            + ". Jeśli to Twój Jozef, dopisz go w jozef.telegram.allowed-chat-ids i uruchom go ponownie.");
                }
                return;
            }
            if (!update.privateChat()) {
                log.warn("Telegram: ignoring a message from non-private chat {}", chat);
                return; // in a group every member could command Jozef
            }
            if (update.forwarded()) {
                api.sendMessage(chat, "Nie wykonuję przekazanych wiadomości — napisz polecenie sam.");
                return;
            }
            long ageSeconds = System.currentTimeMillis() / 1000 - update.dateEpochSeconds();
            if (update.dateEpochSeconds() > 0 && ageSeconds > MAX_AGE_SECONDS) {
                // PC was asleep/offline: a stale "otwórz…" or "przypomnij za 10 minut" must not fire hours later.
                api.sendMessage(chat, "Pominąłem wiadomość sprzed " + (ageSeconds / 60)
                        + " min (komputer był niedostępny): „" + abbreviate(update.text()) + "”. Wyślij ją ponownie, jeśli nadal aktualna.");
                return;
            }
            String text = update.text().trim();
            if (text.equals("/start") || text.equals("/help")) {
                api.sendMessage(chat, "Cześć, tu Jozef. Napisz, co mam zrobić — np. \"co mam jutro w kalendarzu\", "
                        + "\"otwórz YouTube\" (otworzy się na komputerze), \"przypomnij mi za godzinę o praniu\".");
                return;
            }
            AgentResult result = agent.process(text, "telegram:" + chat);
            String reply = result.text() == null || result.text().isBlank() ? "Gotowe." : result.text();
            api.sendMessage(chat, reply.replace("Powiedz tak albo nie.", "Odpisz: tak albo nie."));
        } catch (Exception e) {
            log.warn("Telegram message handling failed ({})", e.getClass().getSimpleName());
        }
    }

    private static String abbreviate(String text) {
        String t = text.trim();
        return t.length() > 60 ? t.substring(0, 57) + "…" : t;
    }

    @Override
    public void shutdown() {
        running = false;
        if (poller != null) {
            poller.interrupt();
        }
        log.info("Telegram capability shut down");
    }
}
