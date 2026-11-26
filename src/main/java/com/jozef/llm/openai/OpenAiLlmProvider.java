package com.jozef.llm.openai;

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
 * LLM provider using the OpenAI Chat Completions API.
 *
 * Also works with any OpenAI-compatible endpoint (DeepSeek, LM Studio, vLLM, …)
 * by configuring {@code jozef.llm.endpoint}.
 *
 * Tool calling follows the protocol strictly: every assistant {@code tool_calls[]}
 * entry keeps the id issued by the API and every tool result carries the matching
 * {@code tool_call_id} — the API rejects the follow-up request otherwise.
 */
@Slf4j
public class OpenAiLlmProvider implements LLMProvider {

    public static final String DEFAULT_ENDPOINT = "https://api.openai.com/v1/chat/completions";

    private final String name;
    private final String model;
    private final String apiKey;
    private final String endpoint;
    private final ObjectMapper mapper = new ObjectMapper();
    private final LlmHttp http;

    public OpenAiLlmProvider(String model, String apiKey, String endpoint) {
        this("openai", model, apiKey, endpoint, Duration.ofSeconds(90));
    }

    public OpenAiLlmProvider(String name, String model, String apiKey, String endpoint, Duration timeout) {
        this(name, model, apiKey, endpoint, new LlmHttp(name, timeout));
    }

    OpenAiLlmProvider(String name, String model, String apiKey, String endpoint, LlmHttp http) {
        this.name = name;
        this.model = model;
        this.apiKey = apiKey;
        this.endpoint = endpoint == null || endpoint.isBlank() ? DEFAULT_ENDPOINT : endpoint;
        this.http = http;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new LlmException(name + ": missing API key (set the environment variable)");
        }
        String json;
        try {
            json = mapper.writeValueAsString(buildRequestBody(request));
        } catch (Exception e) {
            throw new LlmException(name + ": cannot serialize request", e);
        }
        log.debug("{} request: model={}, messages={}, tools={}", name, model,
                request.messages().size(), request.tools() != null ? request.tools().size() : 0);

        String body = http.postJson(endpoint, Map.of("Authorization", "Bearer " + apiKey), json);
        try {
            return parseResponse(body);
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException(name + ": unparsable response", e);
        }
    }

    Map<String, Object> buildRequestBody(ChatRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);

        List<Map<String, Object>> messages = new ArrayList<>();
        if (request.systemPrompt() != null && !request.systemPrompt().isEmpty()) {
            messages.add(Map.of("role", "system", "content", request.systemPrompt()));
        }
        for (ChatMessage msg : request.messages()) {
            messages.add(buildMessage(msg));
        }
        body.put("messages", messages);

        if (request.tools() != null && !request.tools().isEmpty()) {
            body.put("tools", buildToolsJson(request.tools()));
            body.put("tool_choice", request.toolCallsAllowed() ? "auto" : "none");
        }

        // Reasoning models (o-series, gpt-5) reject temperature and the legacy max_tokens.
        boolean reasoningModel = isReasoningModel(model);
        if (!reasoningModel) {
            body.put("temperature", request.temperature());
        }
        if (request.maxTokens() > 0) {
            body.put(reasoningModel ? "max_completion_tokens" : "max_tokens", request.maxTokens());
        }
        return body;
    }

    static boolean isReasoningModel(String model) {
        if (model == null) return false;
        String m = model.toLowerCase();
        return m.startsWith("o1") || m.startsWith("o3") || m.startsWith("o4") || m.startsWith("gpt-5");
    }

    private Map<String, Object> buildMessage(ChatMessage msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", msg.role());

        if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
            m.put("content", msg.content() == null || msg.content().isEmpty() ? null : msg.content());
            List<Map<String, Object>> toolCalls = new ArrayList<>();
            for (ToolCall tc : msg.toolCalls()) {
                Map<String, Object> function = new LinkedHashMap<>();
                function.put("name", tc.name());
                function.put("arguments", toJsonString(tc.arguments()));
                Map<String, Object> call = new LinkedHashMap<>();
                call.put("id", tc.id());
                call.put("type", "function");
                call.put("function", function);
                toolCalls.add(call);
            }
            m.put("tool_calls", toolCalls);
            return m;
        }

        if ("tool".equals(msg.role())) {
            if (msg.toolCallId() != null) {
                m.put("tool_call_id", msg.toolCallId());
            }
            m.put("content", msg.content() == null ? "" : msg.content());
            return m;
        }

        m.put("content", msg.content() == null ? "" : msg.content());
        return m;
    }

    private String toJsonString(Map<String, Object> arguments) {
        try {
            return mapper.writeValueAsString(arguments == null ? Map.of() : arguments);
        } catch (Exception e) {
            return "{}";
        }
    }

    private List<Map<String, Object>> buildToolsJson(List<ToolSchema> tools) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ToolSchema tool : tools) {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", tool.name());
            function.put("description", tool.description());
            function.put("parameters", tool.parameters());
            result.add(Map.of("type", "function", "function", function));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    ChatResponse parseResponse(String body) throws Exception {
        Map<String, Object> json = mapper.readValue(body, new TypeReference<>() {});

        List<Map<String, Object>> choices = (List<Map<String, Object>>) json.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new LlmException(name + ": response has no choices");
        }
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null) {
            throw new LlmException(name + ": response has no message");
        }

        String content = message.get("content") instanceof String s ? s : "";

        List<ToolCall> toolCalls = new ArrayList<>();
        if (message.get("tool_calls") instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> tc)) continue;
                if (!(tc.get("function") instanceof Map<?, ?> function)) continue;
                String toolName = (String) function.get("name");
                if (toolName == null) continue;
                String id = tc.get("id") instanceof String s ? s : null;
                toolCalls.add(ToolCall.of(id, toolName, parseArguments(function.get("arguments"))));
            }
        }
        return new ChatResponse(content, toolCalls);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArguments(Object raw) {
        if (raw == null) return Map.of();
        if (raw instanceof Map<?, ?> map) return (Map<String, Object>) map;
        if (raw instanceof String str) {
            if (str.isBlank()) return Map.of();
            try {
                return mapper.readValue(str, new TypeReference<>() {});
            } catch (Exception e) {
                log.warn("{}: malformed tool arguments ignored: {}", name, str);
                return Map.of();
            }
        }
        return Map.of();
    }
}
