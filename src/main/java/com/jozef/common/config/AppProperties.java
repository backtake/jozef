package com.jozef.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.application")
public record AppProperties(
        String name,
        String version
) {
    public String applicationName() {
        return name != null ? name : "jozef";
    }

    public String applicationVersion() {
        return version != null ? version : "unknown";
    }
}
