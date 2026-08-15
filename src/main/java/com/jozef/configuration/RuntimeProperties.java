package com.jozef.configuration;

public record RuntimeProperties(
        EventBusProperties eventBus,
        HealthProperties health
) {
    public RuntimeProperties {
        if (eventBus == null) eventBus = new EventBusProperties(4);
        if (health == null) health = new HealthProperties(true, 30);
    }

    public record EventBusProperties(int threadPoolSize) {}
    public record HealthProperties(boolean enabled, int checkIntervalSeconds) {}
}
