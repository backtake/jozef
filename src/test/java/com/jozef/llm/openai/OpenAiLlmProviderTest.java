package com.jozef.llm.openai;

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

/** Offline protocol test against a local stub of the Chat Completions endpoint. */
class OpenAiLlmProviderTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastAuth = new AtomicReference<>();
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicReference<String> reply = new AtomicReference<>();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] out = reply.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private OpenAiLlmProvider provider(String model) {
        return new OpenAiLlmProvider("openai", model, "sk-test",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions",
                Duration.ofSeconds(5));
    }

    @Test
    void parsesToolCallsKeepingTheirIds() {
        reply.set("""
                {"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[
                  {"id":"call_abc","type":"function","function":{"name":"open_url","arguments":"{\\"url\\":\\"https://youtube.com\\"}"}},
                  {"id":"call_def","type":"function","function":{"name":"open_url","arguments":"{}"}}]}}]}
                """);
        ChatResponse response = provider("gpt-4o-mini").chat(ChatRequest.simple("sys", "hi", List.of()));

        assertThat(response.content()).isEmpty();
        assertThat(response.toolCalls()).extracting(ToolCall::id).containsExactly("call_abc", "call_def");
        assertThat(response.toolCalls().get(0).arguments()).containsEntry("url", "https://youtube.com");
        assertThat(lastAuth.get()).isEqualTo("Bearer sk-test");
    }

    @Test
    void sendsToolResultsWithMatchingToolCallIds() throws Exception {
        reply.set("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Gotowe\"}}]}");
        ToolCall first = ToolCall.of("call_1", "get_current_time", Map.of());
        ToolCall second = ToolCall.of("call_2", "get_current_time", Map.of());
        List<ChatMessage> messages = List.of(
                ChatMessage.user("która godzina?"),
                ChatMessage.assistantToolCalls(List.of(first, second)),
                ChatMessage.toolResult(first, "12:00"),
                ChatMessage.toolResult(second, "12:00"));
        List<ToolSchema> tools = List.of(new ToolSchema("get_current_time", "time", Map.of("type", "object")));

        ChatResponse response = provider("gpt-4o-mini").chat(new ChatRequest("sys", messages, tools, 0.2, 256));

        assertThat(response.content()).isEqualTo("Gotowe");
        JsonNode body = mapper.readTree(lastBody.get());
        JsonNode sent = body.get("messages");
        assertThat(sent.get(0).get("role").asText()).isEqualTo("system");
        assertThat(sent.get(2).get("tool_calls").get(0).get("id").asText()).isEqualTo("call_1");
        assertThat(sent.get(2).get("tool_calls").get(1).get("id").asText()).isEqualTo("call_2");
        assertThat(sent.get(3).get("tool_call_id").asText()).isEqualTo("call_1");
        assertThat(sent.get(4).get("tool_call_id").asText()).isEqualTo("call_2");
        assertThat(body.get("max_tokens").asInt()).isEqualTo(256);
        assertThat(body.get("temperature").asDouble()).isEqualTo(0.2);
    }

    @Test
    void reasoningModelsUseMaxCompletionTokensAndNoTemperature() throws Exception {
        reply.set("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}]}");
        provider("gpt-5-mini").chat(new ChatRequest("sys", List.of(ChatMessage.user("x")), List.of(), 0.7, 300));

        JsonNode body = mapper.readTree(lastBody.get());
        assertThat(body.has("temperature")).isFalse();
        assertThat(body.get("max_completion_tokens").asInt()).isEqualTo(300);
    }

    @Test
    void httpErrorThrowsInsteadOfReturningErrorTextAsAnswer() {
        status.set(400);
        reply.set("{\"error\":{\"message\":\"bad tool_call_id\"}}");
        assertThatThrownBy(() -> provider("gpt-4o-mini").chat(ChatRequest.simple("s", "x", List.of())))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("400")
                .hasMessageContaining("bad tool_call_id");
    }

    @Test
    void missingApiKeyFailsFast() {
        OpenAiLlmProvider noKey = new OpenAiLlmProvider("deepseek", "deepseek-chat", null,
                "http://127.0.0.1:1/never", Duration.ofSeconds(1));
        assertThatThrownBy(() -> noKey.chat(ChatRequest.simple("s", "x", List.of())))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("API key");
    }

    @Test
    void unreachableEndpointThrows() {
        OpenAiLlmProvider down = new OpenAiLlmProvider("openai", "m", "k",
                "http://127.0.0.1:1/v1/chat/completions", Duration.ofSeconds(2));
        assertThatThrownBy(() -> down.chat(ChatRequest.simple("s", "x", List.of())))
                .isInstanceOf(LlmException.class);
    }
}
