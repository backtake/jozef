package com.jozef.configuration;

/**
 * Proactive background agent configuration.
 *
 * <pre>
 * jozef:
 *   proactive:
 *     enabled: true
 *     calendar-poll-minutes: 5
 *     gmail-poll-minutes: 5
 *     calendar-lookahead-minutes: 15
 * </pre>
 */
public record ProactiveProperties(
        boolean enabled,
        int calendarPollMinutes,
        int gmailPollMinutes,
        int calendarLookaheadMinutes
) {}
