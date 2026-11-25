package com.jozef.llm.ollama;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jozef.llm.ChatMessage;
import com.jozef.llm.ChatRequest;
import com.jozef.llm.ChatResponse;
import com.jozef.configuration.LlmProperties;
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
 * LLM provider using Ollama's local API.
 * Endpoint: http://localhost:11434/api/chat
 *
 * Supports streaming=false, tools, and multi-turn tool calling.
 */
@Slf4j
public class OllamaProvider implements LLMProvider {

    private final String model;
    private final String baseUrl;
    private final ObjectMapper mapper = new ObjectMapper();
    private final LlmHttp http;

    public OllamaProvider(String model, String baseUrl) {
        this(model, baseUrl, Duration.ofSeconds(LlmProperties.DEFAULT_TIMEOUT_SECONDS));
    }

    public OllamaProvider(String model, String baseUrl, Duration timeout) {
        this.model = model;
        this.baseUrl = baseUrl == null || baseUrl.isBlank() ? "http://localhost:11434" : baseUrl;
        this.http = new LlmHttp("ollama", timeout);
    }

    public OllamaProvider(String model) {
        this(model, "http://localhost:11434");
    }

    @Override
    public String name() {
        return "ollama";
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        String json;
        try {
            json = mapper.writeValueAsString(buildRequestBody(request));
        } catch (Exception e) {
            throw new LlmException("ollama: cannot serialize request", e);
        }
        log.debug("Ollama request: model={}, messages={}, tools={}",
                model, request.messages().size(),
                request.tools() != null ? request.tools().size() : 0);

        String body = http.postJson(baseUrl + "/api/chat", Map.of(), json);
        try {
            return parseResponse(body);
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("ollama: unparsable response", e);
        }
    }

    private Map<String, Object> buildRequestBody(ChatRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("stream", false);

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
        }

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("temperature", request.temperature());
        if (request.maxTokens() > 0) {
            options.put("num_predict", request.maxTokens());
        }
        body.put("options", options);

        return body;
    }

    private Map<String, Object> buildMessage(ChatMessage msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", msg.role());

        // Assistant message carrying tool calls (the LLM's request to run tools)
        if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
            m.put("content", msg.content() == null ? "" : msg.content());
            List<Map<String, Object>> toolCalls = new ArrayList<>();
            for (ToolCall tc : msg.toolCalls()) {
                Map<String, Object> function = new LinkedHashMap<>();
                function.put("name", tc.name());
                function.put("arguments", tc.arguments());
                toolCalls.add(Map.of("function", function));
            }
            m.put("tool_calls", toolCalls);
            return m;
        }

        // Tool result message
        if ("tool".equals(msg.role())) {
            m.put("content", msg.content() == null ? "" : msg.content());
            if (msg.toolName() != null && !msg.toolName().isBlank()) {
                m.put("tool_name", msg.toolName());
            }
            return m;
        }

        // Plain user/assistant/system message
        m.put("content", msg.content() == null ? "" : msg.content());
        return m;
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

        Map<String, Object> message = (Map<String, Object>) json.get("message");
        if (message == null) {
            throw new LlmException("ollama: response has no message");
        }

        String content = message.get("content") instanceof String s ? s : "";

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
