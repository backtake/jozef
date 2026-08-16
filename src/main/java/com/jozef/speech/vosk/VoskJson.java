package com.jozef.speech.vosk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Parses the JSON strings returned by Vosk's {@code Recognizer}.
 */
final class VoskJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private VoskJson() {}

    /** Extracts the final {@code "text"} field. */
    static String text(String json) {
        return field(json, "text");
    }

    /** Extracts the streaming {@code "partial"} field. */
    static String partial(String json) {
        return field(json, "partial");
    }

    private static String field(String json, String name) {
        if (json == null || json.isBlank()) {
            return "";
        }
        try {
            JsonNode node = MAPPER.readTree(json).get(name);
            return node == null || node.isNull() ? "" : node.asText();
        } catch (Exception e) {
            return "";
        }
    }
}
