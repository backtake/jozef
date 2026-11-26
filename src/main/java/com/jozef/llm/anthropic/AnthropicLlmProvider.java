package com.jozef.llm.anthropic;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jozef.llm.ChatMessage;
import com.jozef.llm.ChatRequest;
import com.jozef.llm.ChatResponse;
import com.jozef.llm.LLMProvider;
import com.jozef.llm.LlmException;
import com.jozef.llm.LlmHttp;
import com.jozef.llm.ToolCall;
import com.jozef.llm.ToolSchema;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Claude via the Anthropic Messages API ({@code POST /v1/messages}), over plain
 * {@code java.net.http} (the project adds no SDK dependencies — see the
 * llm-adapters spec).
 *
 * <ul>
 *   <li>API version {@code 2026-10-21}: no sampling parameters ({@code temperature}
 *       is rejected), depth is controlled with {@code output_config.effort}.</li>
 *   <li>Tool calling: {@code tool_use} blocks in, all {@code tool_result} blocks of a
 *       step returned together in one user message; failed tools get {@code is_error}.</li>
 *   <li>The assistant's raw content (including {@code thinking} blocks) is replayed
 *       unchanged on the next step, as the API requires.</li>
 *   <li>Server-side fallback on refusals is on by default
 *       ({@code server-side-fallback-2026-07-01}, {@code fallbacks: "default"}).</li>
 * </ul>
 */
@Slf4j
public class AnthropicLlmProvider implements LLMProvider {

    public static final String DEFAULT_ENDPOINT = "https://api.anthropic.com/v1/messages";
    public static final String DEFAULT_MODEL = "claude-opus-5-5";
    static final String API_VERSION = "2026-10-21";
    static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";
    /** Thinking also consumes output tokens — never send a tiny max_tokens. */
    private static final int MIN_MAX_TOKENS = 4096;

    private final String model;
    private final String apiKey;
    private final String endpoint;
    private final String effort;
    private final ObjectMapper mapper = new ObjectMapper();
    private final LlmHttp http;

    public AnthropicLlmProvider(String model, String apiKey, String endpoint, String effort, Duration timeout) {
        this(model, apiKey, endpoint, effort, new LlmHttp("anthropic", timeout));
    }

    AnthropicLlmProvider(String model, String apiKey, String endpoint, String effort, LlmHttp http) {
        this.model = model == null || model.isBlank() ? DEFAULT_MODEL : model;
        this.apiKey = apiKey;
        this.endpoint = endpoint == null || endpoint.isBlank() ? DEFAULT_ENDPOINT : endpoint;
        this.effort = effort == null || effort.isBlank() ? "low" : effort;
        this.http = http;
    }

    @Override
    public String name() {
        return "anthropic";
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new LlmException("anthropic: missing API key (set ANTHROPIC_API_KEY)");
        }
        String json;
        try {
            json = mapper.writeValueAsString(buildRequestBody(request));
        } catch (Exception e) {
            throw new LlmException("anthropic: cannot serialize request", e);
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("x-api-key", apiKey);
        headers.put("anthropic-version", API_VERSION);
        if (supportsServerFallback(model)) {
            headers.put("anthropic-beta", FALLBACK_BETA);
        }
        String body = http.postJson(endpoint, headers, json);
        try {
            return parseResponse(body);
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("anthropic: unparsable response", e);
        }
    }

    static boolean supportsServerFallback(String model) {
        return model.startsWith("claude-opus-5") || model.startsWith("claude-fable-5")
                || model.startsWith("claude-sonnet-5-5");
    }

    Map<String, Object> buildRequestBody(ChatRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", Math.max(MIN_MAX_TOKENS, request.maxTokens()));
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            body.put("system", request.systemPrompt());
        }
        body.put("messages", buildMessages(request.messages()));
        if (request.tools() != null && !request.tools().isEmpty()) {
            List<Map<String, Object>> tools = new ArrayList<>();
            for (ToolSchema tool : request.tools()) {
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("name", tool.name());
                t.put("description", tool.description());
                t.put("input_schema", tool.parameters() == null || tool.parameters().isEmpty()
                        ? Map.of("type", "object", "properties", Map.of())
                        : tool.parameters());
                tools.add(t);
            }
            body.put("tools", tools);
            if (!request.toolCallsAllowed()) {
                body.put("tool_choice", Map.of("type", "none")); // tools stay defined; the model must answer now
            }
        }
        // A voice assistant wants quick answers; depth is the effort knob (no temperature in this API version).
        body.put("output_config", Map.of("effort", effort));
        if (supportsServerFallback(model)) {
            body.put("fallbacks", "default");
        }
        return body;
    }

    /** Maps the neutral conversation to Anthropic turns (tool results grouped into one user turn). */
    List<Map<String, Object>> buildMessages(List<ChatMessage> messages) {
        List<Map<String, Object>> out = new ArrayList<>();
        List<Map<String, Object>> pendingResults = new ArrayList<>();
        for (ChatMessage msg : messages) {
            if ("tool".equals(msg.role())) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("type", "tool_result");
                result.put("tool_use_id", msg.toolCallId());
                String content = msg.content() == null ? "" : msg.content();
                result.put("content", content.isEmpty() ? "(no output)" : content);
                if (content.startsWith("FAILED:")) {
                    result.put("is_error", true);
                }
                pendingResults.add(result);
                continue;
            }
            if (!pendingResults.isEmpty()) {
                out.add(Map.of("role", "user", "content", List.copyOf(pendingResults)));
                pendingResults.clear();
            }
            if ("assistant".equals(msg.role()) && msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                out.add(Map.of("role", "assistant", "content", assistantBlocks(msg)));
            } else if ("assistant".equals(msg.role())) {
                String text = msg.content() == null || msg.content().isBlank() ? "…" : msg.content();
                out.add(Map.of("role", "assistant", "content", text));
            } else {
                String text = msg.content() == null || msg.content().isBlank() ? "…" : msg.content();
                out.add(Map.of("role", "user", "content", text));
            }
        }
        if (!pendingResults.isEmpty()) {
            out.add(Map.of("role", "user", "content", List.copyOf(pendingResults)));
        }
        return mergeConsecutiveUserTurns(out);
    }

    @SuppressWarnings("unchecked")
    private List<Object> assistantBlocks(ChatMessage msg) {
        // Replay the model's own blocks (thinking included) exactly as received.
        if (msg.providerContent() instanceof List<?> raw && !raw.isEmpty()) {
            return (List<Object>) raw;
        }
        List<Object> blocks = new ArrayList<>();
        if (msg.content() != null && !msg.content().isBlank()) {
            blocks.add(Map.of("type", "text", "text", msg.content()));
        }
        for (ToolCall call : msg.toolCalls()) {
            Map<String, Object> block = new LinkedHashMap<>();
            block.put("type", "tool_use");
            block.put("id", call.id());
            block.put("name", call.name());
            block.put("input", call.arguments() == null ? Map.of() : call.arguments());
            blocks.add(block);
        }
        return blocks;
    }

    /** Two user turns in a row (e.g. tool results then a new utterance) must become one. */
    private static List<Map<String, Object>> mergeConsecutiveUserTurns(List<Map<String, Object>> turns) {
        List<Map<String, Object>> merged = new ArrayList<>();
        for (Map<String, Object> turn : turns) {
            if (!merged.isEmpty() && "user".equals(turn.get("role"))
                    && "user".equals(merged.get(merged.size() - 1).get("role"))) {
                Map<String, Object> last = merged.remove(merged.size() - 1);
                List<Object> content = new ArrayList<>(asBlocks(last.get("content")));
                content.addAll(asBlocks(turn.get("content")));
                merged.add(Map.of("role", "user", "content", content));
            } else {
                merged.add(turn);
            }
        }
        return merged;
    }

    private static List<?> asBlocks(Object content) {
        if (content instanceof List<?> list) return list;
        return List.of(Map.of("type", "text", "text", String.valueOf(content)));
    }

    @SuppressWarnings("unchecked")
    ChatResponse parseResponse(String body) throws Exception {
        Map<String, Object> json = mapper.readValue(body, new TypeReference<>() {});
        String stop = json.get("stop_reason") instanceof String s ? s : "";
        if ("refusal".equals(stop)) {
            return new ChatResponse("Przepraszam, w tym nie mogę pomóc.", List.of());
        }
        if (!(json.get("content") instanceof List<?> blocks)) {
            throw new LlmException("anthropic: response has no content");
        }
        StringBuilder text = new StringBuilder();
        List<ToolCall> calls = new ArrayList<>();
        for (Object item : blocks) {
            if (!(item instanceof Map<?, ?> block)) continue;
            Object type = block.get("type");
            if ("text".equals(type) && block.get("text") instanceof String t) {
                if (text.length() > 0) text.append(' ');
                text.append(t);
            } else if ("tool_use".equals(type) && block.get("name") instanceof String toolName) {
                Map<String, Object> input = block.get("input") instanceof Map<?, ?> m
                        ? (Map<String, Object>) m : Map.of();
                calls.add(ToolCall.of(block.get("id") instanceof String id ? id : null, toolName, input));
            }
        }
        // Keep the raw blocks only when a tool step follows (they must be replayed unchanged).
        return new ChatResponse(text.toString().trim(), calls, calls.isEmpty() ? null : blocks);
    }
}
