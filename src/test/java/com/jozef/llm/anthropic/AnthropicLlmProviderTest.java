package com.jozef.llm.anthropic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jozef.llm.ChatMessage;
import com.jozef.llm.ChatRequest;
import com.jozef.llm.ChatResponse;
import com.jozef.llm.LlmException;
import com.jozef.llm.ToolCall;
import com.jozef.llm.ToolSchema;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Offline protocol test against a local stub of /v1/messages. */
class AnthropicLlmProviderTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<Map<String, String>> lastHeaders = new AtomicReference<>();
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicReference<String> reply = new AtomicReference<>();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", ex -> {
            lastBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastHeaders.set(Map.of(
                    "x-api-key", String.valueOf(ex.getRequestHeaders().getFirst("x-api-key")),
                    "anthropic-version", String.valueOf(ex.getRequestHeaders().getFirst("anthropic-version")),
                    "anthropic-beta", String.valueOf(ex.getRequestHeaders().getFirst("anthropic-beta"))));
            byte[] out = reply.get().getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status.get(), out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private AnthropicLlmProvider provider() {
        return new AnthropicLlmProvider(null, "sk-ant-test",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/messages", "low", Duration.ofSeconds(5));
    }

    @Test
    void parsesToolUseAndKeepsRawBlocksForReplay() {
        reply.set("""
                {"stop_reason":"tool_use","content":[
                  {"type":"thinking","thinking":"","signature":"sig"},
                  {"type":"text","text":"Sprawdzam."},
                  {"type":"tool_use","id":"toolu_1","name":"calendar_events_on_day","input":{"date":"tomorrow"}}]}
                """);
        ChatResponse r = provider().chat(ChatRequest.simple("sys", "co mam jutro?", List.of()));

        assertThat(r.content()).isEqualTo("Sprawdzam.");
        assertThat(r.toolCalls()).singleElement().satisfies(c -> {
            assertThat(c.id()).isEqualTo("toolu_1");
            assertThat(c.arguments()).containsEntry("date", "tomorrow");
        });
        assertThat(r.providerContent()).isInstanceOf(List.class);
        assertThat(lastHeaders.get()).containsEntry("x-api-key", "sk-ant-test")
                .containsEntry("anthropic-version", "2026-10-21")
                .containsEntry("anthropic-beta", "server-side-fallback-2026-07-01");
    }

    @Test
    void sendsGroupedToolResultsReplaysThinkingAndNoTemperature() throws Exception {
        reply.set("{\"stop_reason\":\"end_turn\",\"content\":[{\"type\":\"text\",\"text\":\"Jutro masz dwa spotkania.\"}]}");
        ToolCall a = ToolCall.of("toolu_a", "x", Map.of());
        ToolCall b = ToolCall.of("toolu_b", "y", Map.of());
        List<Object> raw = List.of(
                Map.of("type", "thinking", "thinking", "", "signature", "sig"),
                Map.of("type", "tool_use", "id", "toolu_a", "name", "x", "input", Map.of()),
                Map.of("type", "tool_use", "id", "toolu_b", "name", "y", "input", Map.of()));
        List<ChatMessage> messages = List.of(
                ChatMessage.user("co mam jutro?"),
                ChatMessage.assistantToolCalls("", List.of(a, b), raw),
                ChatMessage.toolResult(a, "spotkanie 1"),
                ChatMessage.toolResult(b, "FAILED: brak dostępu"));
        List<ToolSchema> tools = List.of(new ToolSchema("x", "X", Map.of()), new ToolSchema("y", "Y", Map.of()));

        ChatResponse r = provider().chat(new ChatRequest("sys", messages, tools, 0.7, 100, false));

        assertThat(r.content()).isEqualTo("Jutro masz dwa spotkania.");
        JsonNode body = mapper.readTree(lastBody.get());
        assertThat(body.has("temperature")).isFalse();
        assertThat(body.get("max_tokens").asInt()).isGreaterThanOrEqualTo(4096);
        assertThat(body.get("system").asText()).isEqualTo("sys");
        assertThat(body.get("output_config").get("effort").asText()).isEqualTo("low");
        assertThat(body.get("fallbacks").asText()).isEqualTo("default");
        assertThat(body.get("tool_choice").get("type").asText()).isEqualTo("none");
        assertThat(body.get("tools").get(0).get("input_schema").get("type").asText()).isEqualTo("object");

        JsonNode sent = body.get("messages");
        assertThat(sent).hasSize(3);
        assertThat(sent.get(1).get("content").get(0).get("type").asText()).isEqualTo("thinking");
        JsonNode results = sent.get(2).get("content");
        assertThat(sent.get(2).get("role").asText()).isEqualTo("user");
        assertThat(results).hasSize(2);
        assertThat(results.get(0).get("tool_use_id").asText()).isEqualTo("toolu_a");
        assertThat(results.get(1).get("is_error").asBoolean()).isTrue();
    }

    @Test
    void toolResultsFollowedByAUserTurnAreMergedIntoOneUserMessage() throws Exception {
        reply.set("{\"stop_reason\":\"end_turn\",\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}");
        ToolCall a = ToolCall.of("toolu_a", "send", Map.of("to", "x"));
        List<ChatMessage> messages = List.of(
                ChatMessage.user("wyślij"),
                ChatMessage.assistant("Potwierdź: …"),
                ChatMessage.user("tak"),
                ChatMessage.assistantToolCalls(List.of(a)),
                ChatMessage.toolResult(a, "sent"));
        provider().chat(new ChatRequest("sys", messages, List.of(new ToolSchema("send", "s", Map.of())), 0.1, 100));

        JsonNode sent = mapper.readTree(lastBody.get()).get("messages");
        for (int i = 1; i < sent.size(); i++) {
            assertThat(sent.get(i).get("role").asText()).isNotEqualTo(sent.get(i - 1).get("role").asText());
        }
    }

    @Test
    void refusalBecomesAPoliteAnswer() {
        reply.set("{\"stop_reason\":\"refusal\",\"content\":[]}");
        assertThat(provider().chat(ChatRequest.simple("s", "x", List.of())).content()).contains("nie mogę");
    }

    @Test
    void errorsAndMissingKeyThrow() {
        status.set(401);
        reply.set("{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\"}}");
        assertThatThrownBy(() -> provider().chat(ChatRequest.simple("s", "x", List.of())))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.userHint()).contains("klucz API"));

        AnthropicLlmProvider noKey = new AnthropicLlmProvider(null, "", null, null, Duration.ofSeconds(1));
        assertThatThrownBy(() -> noKey.chat(ChatRequest.simple("s", "x", List.of())))
                .hasMessageContaining("ANTHROPIC_API_KEY");
    }
}
