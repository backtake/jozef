package com.jozef.configuration;

public record WakeWordProperties(
        boolean enabled,
        String word
) {}
