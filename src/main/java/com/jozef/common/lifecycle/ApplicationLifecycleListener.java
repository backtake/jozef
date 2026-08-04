package com.jozef.common.lifecycle;

import com.jozef.common.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ApplicationLifecycleListener {

    private static final Logger log = LoggerFactory.getLogger(ApplicationLifecycleListener.class);

    private final AppProperties appProperties;

    public ApplicationLifecycleListener(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("======================================");
        log.info("  {} started", appProperties.applicationName().toUpperCase());
        log.info("  Version: {}", appProperties.applicationVersion());
        log.info("  Java: {}", System.getProperty("java.version"));
        log.info("======================================");
    }

    @EventListener(ContextClosedEvent.class)
    public void onShutdown() {
        log.info("======================================");
        log.info("  {} stopped", appProperties.applicationName().toUpperCase());
        log.info("======================================");
    }
}
