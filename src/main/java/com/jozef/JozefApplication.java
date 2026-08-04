package com.jozef;

import com.jozef.common.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class JozefApplication {

    private static final Logger log = LoggerFactory.getLogger(JozefApplication.class);

    private final AppProperties appProperties;

    public JozefApplication(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public static void main(String[] args) {
        SpringApplication.run(JozefApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("======================================");
        log.info("  {} started", appProperties.applicationName().toUpperCase());
        log.info("  Version: {}", appProperties.applicationVersion());
        log.info("  Java: {}", System.getProperty("java.version"));
        log.info("======================================");
    }
}
