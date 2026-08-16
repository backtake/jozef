package com.jozef.google.gmail;

/**
 * A single Gmail message as exposed to the agent.
 */
public record GmailMessage(
        String id,
        String threadId,
        String from,
        String subject,
        String snippet,
        String body
) {}
