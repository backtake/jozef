package com.jozef.llm.openai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jozef.llm.ChatMessage;
import com.jozef.llm.ChatRequest;
import com.jozef.llm.ChatResponse;
import com.jozef.llm.LLMProvider;
import com.jozef.llm.ToolCall;
import com.jozef.llm.ToolSchema;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM provider using the OpenAI Chat Completions API.
 *
 * Also works with any OpenAI-compatible endpoint (LM Studio, AnythingLLM, vLLM, …)
 * by configuring {@code jozef.llm.endpoint}.
 */
@Slf4j
public class OpenAiLlmProvider implements LLMProvider {

    private final String model;
    private final String apiKey;
    private final String endpoint;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public OpenAiLlmProvider(String model, String apiKey, String endpoint) {
        this.model = model;
        this.apiKey = apiKey;
        this.endpoint = endpoint == null || endpoint.isBlank()
                ? "https://api.openai.com/v1/chat/completions"
                : endpoint;
        this.mapper = new ObjectMapper();
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public String name() {
        return "openai";
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        try {
            Map<String, Object> body = buildRequestBody(request);
            String json = mapper.writeValueAsString(body);

            log.debug("OpenAI request: model={}, messages={}, tools={}",
                    model, request.messages().size(),
                    request.tools() != null ? request.tools().size() : 0);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json));

            if (apiKey != null && !apiKey.isBlank()) {
                builder.header("Authorization", "Bearer " + apiKey);
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("OpenAI returned {}: {}", response.statusCode(), response.body());
                return new ChatResponse("Error: OpenAI returned " + response.statusCode(), List.of());
            }

            return parseResponse(response.body());

        } catch (Exception e) {
            log.error("OpenAI request failed", e);
            return new ChatResponse("Error: " + e.getMessage(), List.of());
        }
    }

    private Map<String, Object> buildRequestBody(ChatRequest request) {
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
            body.put("tool_choice", "auto");
        }

        body.put("temperature", request.temperature());
        if (request.maxTokens() > 0) {
            body.put("max_tokens", request.maxTokens());
        }

        return body;
    }

    private Map<String, Object> buildMessage(ChatMessage msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", msg.role());

        if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
            m.put("content", msg.content() == null ? null : msg.content());
            List<Map<String, Object>> toolCalls = new ArrayList<>();
            for (ToolCall tc : msg.toolCalls()) {
                Map<String, Object> function = new LinkedHashMap<>();
                function.put("name", tc.name());
                function.put("arguments", toJsonString(tc.arguments()));
                toolCalls.add(Map.of("id", "call_" + tc.name(), "type", "function", "function", function));
            }
            m.put("tool_calls", toolCalls);
            return m;
        }

        if ("tool".equals(msg.role())) {
            // OpenAI tool results carry the tool call id; we keep a simple shape.
            m.put("content", msg.content() == null ? "" : msg.content());
            return m;
        }

        m.put("content", msg.content() == null ? "" : msg.content());
        return m;
    }

    private String toJsonString(Map<String, Object> arguments) {
        try {
            return mapper.writeValueAsString(arguments);
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
    private ChatResponse parseResponse(String body) throws Exception {
        Map<String, Object> json = mapper.readValue(body, new TypeReference<>() {});

        List<Map<String, Object>> choices = (List<Map<String, Object>>) json.get("choices");
        if (choices == null || choices.isEmpty()) {
            return new ChatResponse("No choices in response", List.of());
        }

        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null) {
            return new ChatResponse("No message in response", List.of());
        }

        String content = (String) message.get("content");

        List<ToolCall> toolCalls = new ArrayList<>();
        Object rawToolCalls = message.get("tool_calls");
        if (rawToolCalls instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> tc)) continue;
                Map<String, Object> function = (Map<String, Object>) tc.get("function");
                if (function == null) continue;

                String name = (String) function.get("name");
                Map<String, Object> arguments = parseArguments(function.get("arguments"));
                toolCalls.add(ToolCall.of(name, arguments));
            }
        }

        return new ChatResponse(content != null ? content : "", toolCalls);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArguments(Object raw) throws Exception {
        if (raw == null) return Map.of();
        if (raw instanceof Map<?, ?> map) return (Map<String, Object>) map;
        if (raw instanceof String str) {
            if (str.isBlank()) return Map.of();
            return mapper.readValue(str, new TypeReference<>() {});
        }
        return Map.of();
    }
}
