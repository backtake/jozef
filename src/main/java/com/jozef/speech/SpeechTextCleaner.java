package com.jozef.speech;

/**
 * Cleans LLM output before it is spoken, removing markdown/formatting
 * that a text-to-speech engine would read out literally.
 */
public final class SpeechTextCleaner {

    private SpeechTextCleaner() {}

    public static String clean(String text) {
        if (text == null) {
            return "";
        }
        String t = text
                .replaceAll("(?s)```.*?```", " ")           // fenced code blocks
                .replaceAll("`([^`]*)`", "$1")              // inline code
                .replaceAll("\\*\\*([^*]+)\\*\\*", "$1")    // bold
                .replaceAll("\\*([^*]+)\\*", "$1")          // italic
                .replaceAll("__([^_]+)__", "$1")            // bold (alt)
                .replaceAll("_([^_]+)_", "$1")              // italic (alt)
                .replaceAll("(?m)^\\s*#{1,6}\\s*", "")      // markdown headings
                .replaceAll("(?m)^\\s*[-*+]\\s+", "")       // bullet lists
                .replaceAll("(?m)^\\s*\\d+[.)]\\s+", "")    // numbered lists
                .replaceAll("\\[([^\\]]+)\\]\\([^)]*\\)", "$1") // links → text
                .replaceAll("\\s+", " ")                    // collapse whitespace
                .trim();
        return t;
    }
}
