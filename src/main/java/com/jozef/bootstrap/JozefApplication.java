package com.jozef.bootstrap;

import com.jozef.configuration.CoreConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * Entry point for Jozef AI Agent.
 *
 * No web server. No REST. No controllers.
 * Spring is used ONLY as a DI container.
 *
 * On startup:
 *   1. Spring Boot initializes the context
 *   2. CoreConfiguration wires all beans
 *   3. JozefBootstrapRunner calls ApplicationLifecycle.start()
 *   4. Capabilities initialize; the agent voice loop starts listening
 */
@SpringBootApplication
@Import(CoreConfiguration.class)
public class JozefApplication {

    public static void main(String[] args) {
        if (!SingleInstance.acquire(java.nio.file.Path.of("data", "jozef.lock").toAbsolutePath())) {
            String message = "Jozef już działa (ikona w zasobniku obok zegara). Druga kopia nie zostanie uruchomiona.";
            System.err.println(message);
            if (!java.awt.GraphicsEnvironment.isHeadless()) {
                javax.swing.JOptionPane.showMessageDialog(null, message, "Jozef",
                        javax.swing.JOptionPane.INFORMATION_MESSAGE);
            }
            System.exit(0);
            return;
        }
        // Disable web environment — we are NOT a web application
        SpringApplication app = new SpringApplication(JozefApplication.class);
        app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
        // Enable AWT (Robot) — Spring Boot forces headless=true by default, which
        // would disable mouse/keyboard/screenshot automation.
        app.setHeadless(false);
        app.run(args);
    }
}
