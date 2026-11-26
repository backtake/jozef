package com.jozef.channels.telegram;

import com.jozef.configuration.AgentProperties;
import com.jozef.configuration.LlmProperties;
import com.jozef.configuration.MemoryProperties;
import com.jozef.configuration.TelegramProperties;
import com.jozef.core.agent.AgentRuntime;
import com.jozef.core.events.Event;
import com.jozef.core.events.EventBus;
import com.jozef.core.events.EventListener;
import com.jozef.core.security.ConversationalConfirmationService;
import com.jozef.core.security.PermissionManager;
import com.jozef.core.session.SessionManager;
import com.jozef.core.tool.ToolRegistry;
import com.jozef.llm.ChatResponse;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramCapabilityTest {

    @TempDir
    Path dir;

    private HttpServer server;
    private final List<String> sent = new CopyOnWriteArrayList<>();
    private final AtomicInteger llmCalls = new AtomicInteger();
    private TelegramBotApi api;
    private AgentRuntime agent;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/botTEST/sendMessage", ex -> {
            sent.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] ok = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, ok.length);
            ex.getResponseBody().write(ok);
            ex.close();
        });
        server.start();
        api = new TelegramBotApi("http://127.0.0.1:" + server.getAddress().getPort() + "/botTEST", HttpClient.newHttpClient());

        EventBus bus = new EventBus() {
            @Override public void publish(Event event) {}
            @Override public <E extends Event> void register(Class<E> type, EventListener<E> l) {}
        };
        agent = new AgentRuntime(new com.jozef.llm.LLMProvider() {
            @Override public String name() { return "fake"; }
            @Override public ChatResponse chat(com.jozef.llm.ChatRequest request) {
                llmCalls.incrementAndGet();
                return new ChatResponse("Jutro masz dentystę o 10.", List.of());
            }
        }, new ToolRegistry(), () -> "sys", bus, new SessionManager(dir.resolve("s.json").toString()),
                new PermissionManager(), new ConversationalConfirmationService(), new AgentProperties(5, 10),
                new LlmProperties("x", "m", null, null, 0.2, 100, null), null, new MemoryProperties(20, 5, null, null));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private TelegramCapability capability(List<Long> allowed) {
        TelegramCapability c = new TelegramCapability(new TelegramProperties(true, "T", allowed), agent, k -> null);
        c.initialize(); // no token → does not start polling
        return c;
    }

    @Test
    void ownerGetsAnswersFromTheAgent() throws Exception {
        TelegramCapability c = capability(List.of(42L));
        injectApi(c);

        c.handle(new TelegramBotApi.Update(1, 42L, "co mam jutro?", "Owner"));

        assertThat(llmCalls).hasValue(1);
        assertThat(sent).singleElement().satisfies(body -> assertThat(body).contains("dentyst").contains("42"));
    }

    @Test
    void strangersNeverReachTheAgentAndLearnOnlyTheirChatIdOnce() throws Exception {
        TelegramCapability c = capability(List.of(42L));
        injectApi(c);

        c.handle(new TelegramBotApi.Update(1, 777L, "wyślij maila do szefa", "Mallory"));
        c.handle(new TelegramBotApi.Update(2, 777L, "tak", "Mallory"));

        assertThat(llmCalls).hasValue(0);
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0)).contains("777").doesNotContain("dentyst");
    }

    @Test
    void staleForwardedAndGroupMessagesAreNotExecuted() throws Exception {
        TelegramCapability c = capability(List.of(42L));
        injectApi(c);
        long now = System.currentTimeMillis() / 1000;

        c.handle(new TelegramBotApi.Update(1, 42L, "otwórz YouTube", "Owner", now - 3600, true, false));
        c.handle(new TelegramBotApi.Update(2, 42L, "wyślij maila do szefa", "Owner", now, true, true));
        c.handle(new TelegramBotApi.Update(3, 42L, "otwórz YouTube", "Owner", now, false, false));

        assertThat(llmCalls).hasValue(0);
        assertThat(sent).hasSize(2);
        assertThat(sent.get(0)).contains("Pominąłem");
        assertThat(sent.get(1)).contains("przekazanych");
    }

    @Test
    void parsesUpdateMetadata() throws Exception {
        List<TelegramBotApi.Update> updates = api.parseUpdates("""
                {"ok":true,"result":[
                  {"update_id":5,"message":{"date":1700000000,"chat":{"id":42,"type":"private"},"text":"a"}},
                  {"update_id":6,"message":{"date":1,"chat":{"id":-9,"type":"group"},"forward_origin":{},"text":"b"}}]}
                """);
        assertThat(updates.get(0).privateChat()).isTrue();
        assertThat(updates.get(0).dateEpochSeconds()).isEqualTo(1700000000L);
        assertThat(updates.get(1).privateChat()).isFalse();
        assertThat(updates.get(1).forwarded()).isTrue();
    }

    @Test
    void parsesUpdates() throws Exception {
        List<TelegramBotApi.Update> updates = api.parseUpdates("""
                {"ok":true,"result":[
                  {"update_id":10,"message":{"chat":{"id":42},"from":{"first_name":"Stas"},"text":"hej"}},
                  {"update_id":11,"message":{"chat":{"id":42},"photo":[]}},
                  {"update_id":12,"edited_message":{}}]}
                """);
        assertThat(updates).extracting(TelegramBotApi.Update::updateId).containsExactly(10L, 11L, 12L);
        assertThat(updates.get(0).text()).isEqualTo("hej");
        assertThat(updates.get(1).text()).isNull();
    }

    private void injectApi(TelegramCapability c) throws Exception {
        var field = TelegramCapability.class.getDeclaredField("api");
        field.setAccessible(true);
        field.set(c, api);
    }
}
