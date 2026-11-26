package com.jozef.automation.actions;

/**
 * Remembers what Jozef typed recently, so that confirming "press Enter" can say
 * what exactly will be submitted ("wcześniej wpisano: …").
 */
public class TypingMemory {

    private static final long RECENT_MILLIS = 5 * 60_000;

    private volatile String lastText;
    private volatile long lastAt;

    public void typed(String text) {
        lastText = text;
        lastAt = System.currentTimeMillis();
    }

    /** The last typed text if it was typed within the last few minutes, else {@code null}. */
    public String recentText() {
        String text = lastText;
        return text != null && System.currentTimeMillis() - lastAt < RECENT_MILLIS ? text : null;
    }
}
