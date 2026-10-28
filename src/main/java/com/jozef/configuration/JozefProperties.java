package com.jozef.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Root configuration, bound from the {@code jozef.*} YAML namespace.
 */
@ConfigurationProperties(prefix = "jozef")
public record JozefProperties(
        LlmProperties llm,
        SpeechProperties speech,
        OverlayProperties overlay,
        WakeWordProperties wakeword,
        AutomationProperties automation,
        PluginsProperties plugins,
        MemoryProperties memory,
        AgentProperties agent,
        PersonaProperties persona,
        BrowserProperties browser,
        GoogleProperties google,
        SecurityProperties security,
        TrayProperties tray,
        ProactiveProperties proactive,
        PlanningProperties planning,
        RuntimeProperties runtime
) {
    public JozefProperties {
        if (llm == null) llm = new LlmProperties("ollama", "llama3.2", "http://localhost:11434", null, 0.7, 2048, null);
        if (speech == null) speech = new SpeechProperties(null, null, null);
        if (overlay == null) overlay = new OverlayProperties(true, "top-right", 0.9, 5000);
        if (wakeword == null) wakeword = new WakeWordProperties(false, "hej jozef");
        if (automation == null) automation = new AutomationProperties("windows", 1.0, 1.0);
        if (plugins == null) plugins = new PluginsProperties(true, List.of("plugins/"), List.of());
        if (memory == null) memory = new MemoryProperties(50, 10, "data/memory.json", null);
        if (agent == null) agent = new AgentProperties(5, 30);
        if (persona == null) persona = new PersonaProperties("Jozef", "pl", "concise");
        if (browser == null) browser = new BrowserProperties(true, false, "chrome", null, null);
        if (google == null) google = new GoogleProperties(true, null, null, "data/google-tokens.json", "primary", "data/google-credentials.json", true);
        if (security == null) security = new SecurityProperties("console");
        if (tray == null) tray = new TrayProperties(true, false, "ctrl alt J");
        if (proactive == null) proactive = new ProactiveProperties(true, 5, 5, 15);
        if (planning == null) planning = new PlanningProperties(true, 3, "data/plans.json");
        if (runtime == null) runtime = new RuntimeProperties(null, null);
    }
}
