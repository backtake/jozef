package com.jozef.configuration;

public record OverlayProperties(
        boolean enabled,
        String position,
        double opacity,
        int timeoutMs
) {}
