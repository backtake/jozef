package com.jozef.configuration;

import com.jozef.assistant.AssistantCapability;
import com.jozef.automation.AutomationCapability;
import com.jozef.automation.apps.InstalledAppsCatalog;
import com.jozef.automation.apps.WindowsStartAppsCatalog;
import com.jozef.automation.launch.Launcher;
import com.jozef.shortcuts.QuickCommands;
import com.jozef.shortcuts.ShortcutStore;
import com.jozef.browser.BrowserCapability;
import com.jozef.channels.telegram.TelegramCapability;
import com.jozef.core.agent.AgentRuntime;
import com.jozef.core.builtin.GetTimeAction;
import com.jozef.core.builtin.StatusAction;
import com.jozef.core.capabilities.Capability;
import com.jozef.core.capabilities.CapabilityLoader;
import com.jozef.core.commands.CommandBus;
import com.jozef.core.commands.CommandBusImpl;
import com.jozef.core.context.ConfigurableSystemPromptProvider;
import com.jozef.core.context.ContextAggregator;
import com.jozef.core.context.DefaultContextAggregator;
import com.jozef.core.context.SystemPromptProvider;
import com.jozef.core.events.AsyncEventBus;
import com.jozef.core.events.Event;
import com.jozef.core.events.EventBus;
import com.jozef.core.health.HealthMonitor;
import com.jozef.core.lifecycle.ApplicationLifecycle;
import com.jozef.core.logging.EventLogger;
import com.jozef.core.memory.FileLongTermMemory;
import com.jozef.core.memory.LongTermMemory;
import com.jozef.core.memory.MemoryCapability;
import com.jozef.core.plugins.Plugin;
import com.jozef.core.plugins.PluginLoader;
import com.jozef.core.security.AutoDenyConfirmationService;
import com.jozef.core.security.ConfirmationService;
import com.jozef.core.security.ConsoleConfirmationService;
import com.jozef.core.security.ConversationalConfirmationService;
import com.jozef.core.security.PermissionManager;
import com.jozef.core.session.SessionManager;
import com.jozef.core.tool.ToolRegistry;
import com.jozef.google.GoogleCapability;
import com.jozef.llm.LLMCapability;
import com.jozef.overlay.OverlayCapability;
import com.jozef.planning.PlanningCapability;
import com.jozef.planning.PlanStore;
import com.jozef.planning.FilePlanStore;
import com.jozef.proactive.ProactiveAgentService;
import com.jozef.proactive.ProactiveCapability;
import com.jozef.speech.SpeechCapability;
import com.jozef.tray.TrayCapability;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Core configuration — wires the entire runtime.
 *
 * No web, no REST, no controllers. Just the event-driven agent engine.
 */
@Configuration
@EnableConfigurationProperties(JozefProperties.class)
@Slf4j
public class CoreConfiguration {

    // ─── Executor Service ───────────────────────────

    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService eventExecutor(JozefProperties props) {
        int poolSize = props.runtime().eventBus().threadPoolSize();
        return Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "jozef-event");
            t.setDaemon(true);
            return t;
        });
    }

    // ─── Event Bus ──────────────────────────────────

    @Bean(destroyMethod = "close")
    public EventBus eventBus(ExecutorService eventExecutor, EventLogger eventLogger) {
        AsyncEventBus bus = new AsyncEventBus(eventExecutor);
        bus.register(Event.class, eventLogger);
        return bus;
    }

    @Bean
    public EventLogger eventLogger() {
        return new EventLogger();
    }

    // ─── Command Bus ────────────────────────────────

    @Bean
    public CommandBus commandBus() {
        return new CommandBusImpl();
    }

    // ─── Tool Registry (built-in tools) ─────────────

    @Bean
    public ToolRegistry toolRegistry() {
        ToolRegistry registry = new ToolRegistry();
        // Built-in, always-available tools.
        registry.register(new GetTimeAction());
        return registry;
    }

    // ─── Security ───────────────────────────────────

    @Bean
    public PermissionManager permissionManager() {
        return new PermissionManager();
    }

    @Bean
    public ConfirmationService confirmationService(JozefProperties props) {
        // Default: confirm in the conversation itself ("Mam wysłać? — Tak") — works
        // for voice, the command window and the phone; a background app has no console.
        String provider = props.security() != null && props.security().confirmationProvider() != null
                ? props.security().confirmationProvider().toLowerCase()
                : "conversation";
        return switch (provider) {
            case "conversation" -> new ConversationalConfirmationService();
            case "auto-deny" -> new AutoDenyConfirmationService();
            case "console" -> new ConsoleConfirmationService();
            default -> {
                log.warn("Unknown confirmation provider '{}' — using conversation.", provider);
                yield new ConversationalConfirmationService();
            }
        };
    }

    // ─── Plugin Loader ──────────────────────────────

    @Bean
    public PluginLoader pluginLoader(ToolRegistry toolRegistry,
                                     JozefProperties props,
                                     List<Plugin> plugins) {
        PluginLoader loader = new PluginLoader(toolRegistry, props.plugins());
        loader.loadAll(plugins);
        return loader;
    }

    // ─── Session ────────────────────────────────────

    @Bean
    public SessionManager sessionManager() {
        return new SessionManager();
    }

    // ─── Context / Persona ──────────────────────────

    @Bean
    public SystemPromptProvider systemPromptProvider(JozefProperties props) {
        return new ConfigurableSystemPromptProvider(props.persona());
    }

    @Bean
    public LongTermMemory longTermMemory(JozefProperties props) {
        return new FileLongTermMemory(props.memory().file());
    }

    @Bean
    public ContextAggregator contextAggregator(LongTermMemory longTermMemory, JozefProperties props) {
        return new DefaultContextAggregator(longTermMemory, props.memory().workingMemorySize());
    }

    // ─── Agent Runtime ──────────────────────────────

    @Bean
    public AgentRuntime agentRuntime(LLMCapability llm,
                                     ToolRegistry toolRegistry,
                                     SystemPromptProvider systemPromptProvider,
                                     EventBus eventBus,
                                     SessionManager sessionManager,
                                     PermissionManager permissionManager,
                                     ConfirmationService confirmationService,
                                     ContextAggregator contextAggregator,
                                     JozefProperties props) {
        return new AgentRuntime(
                llm.provider(),
                toolRegistry,
                systemPromptProvider,
                eventBus,
                sessionManager,
                permissionManager,
                confirmationService,
                props.agent(),
                props.llm(),
                contextAggregator,
                props.memory());
    }

    // ─── Capability Loader ──────────────────────────

    @Bean
    public CapabilityLoader capabilityLoader(List<Capability> capabilities, EventBus eventBus) {
        return new CapabilityLoader(capabilities, eventBus);
    }

    // ─── Health Monitor ─────────────────────────────

    @Bean
    public HealthMonitor healthMonitor(EventBus eventBus, JozefProperties props) {
        return new HealthMonitor(eventBus, props.runtime().health().checkIntervalSeconds());
    }

    // ─── Application Lifecycle ──────────────────────

    @Bean
    public ApplicationLifecycle applicationLifecycle(
            CapabilityLoader capabilityLoader,
            EventBus eventBus,
            @Value("${spring.application.version:0.0.1}") String version) {
        return new ApplicationLifecycle(capabilityLoader, eventBus, version);
    }

    // ─── Capabilities ──────────────────────────────

    @Bean
    public SpeechCapability speechCapability(JozefProperties props, LLMCapability llmCapability) {
        return new SpeechCapability(props.speech(), props.wakeword(), llmCapability.resolvedProvider());
    }

    @Bean
    public LLMCapability llmCapability(JozefProperties props) {
        return new LLMCapability(props.llm());
    }

    @Bean
    public AutomationCapability automationCapability(JozefProperties props, ToolRegistry toolRegistry,
                                                     Launcher launcher, ShortcutStore shortcutStore) {
        return new AutomationCapability(props.automation(), toolRegistry, launcher, shortcutStore);
    }

    // ─── Launching sites & apps / user shortcuts ─────

    @Bean
    public InstalledAppsCatalog installedAppsCatalog() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return InstalledAppsCatalog.empty();
        }
        WindowsStartAppsCatalog catalog = new WindowsStartAppsCatalog();
        // Warm the cache off the startup path (~1 s PowerShell scan).
        Thread warmup = new Thread(catalog::apps, "jozef-apps-scan");
        warmup.setDaemon(true);
        warmup.start();
        return catalog;
    }

    @Bean
    public Launcher launcher(InstalledAppsCatalog catalog) {
        return new Launcher(catalog, Launcher.windowsOpener(), null);
    }

    @Bean
    public ShortcutStore shortcutStore(@Value("${jozef.shortcuts.file:data/shortcuts.yml}") String file) {
        return new ShortcutStore(file);
    }

    @Bean
    public StatusAction statusAction(ToolRegistry toolRegistry, LLMCapability llm, AgentRuntime agentRuntime,
                                     SpeechCapability speech, GoogleCapability google,
                                     TelegramCapability telegram, ShortcutStore shortcutStore,
                                     InstalledAppsCatalog catalog) {
        StatusAction status = new StatusAction()
                .add("Model językowy", () -> llm.provider().name()
                        + (agentRuntime.lastLlmProblem() == null ? "" : " — problem: " + agentRuntime.lastLlmProblem()))
                .add("Głos", () -> speech.synthesizer().name())
                .add("Rozpoznawanie mowy", () -> speech.recognizer().name()
                        + (speech.wakeWordDetector() == null ? ", bez słowa budzącego" : ", słowo budzące włączone"))
                .add("Google (Gmail, Kalendarz)", () -> google.calendar() != null ? "połączone" : "niepołączone")
                .add("Telefon (Telegram)", telegram::status)
                .add("Skróty", () -> shortcutStore.all().size() + " zapisanych")
                .add("Programy z Menu Start", () -> catalog.apps().size() + " znanych");
        toolRegistry.register(status);
        return status;
    }

    @Bean
    public QuickCommands quickCommands(ShortcutStore shortcutStore, InstalledAppsCatalog catalog,
                                       Launcher launcher, AgentRuntime agentRuntime) {
        QuickCommands quick = new QuickCommands(shortcutStore, catalog, launcher);
        agentRuntime.addFastPath(quick);
        agentRuntime.addFastPath(new com.jozef.shortcuts.MediaCommands());
        return quick;
    }

    @Bean
    public BrowserCapability browserCapability(JozefProperties props, ToolRegistry toolRegistry) {
        return new BrowserCapability(props.browser(), toolRegistry);
    }

    @Bean
    public GoogleCapability googleCapability(JozefProperties props, ToolRegistry toolRegistry) {
        return new GoogleCapability(props.google(), toolRegistry);
    }

    @Bean
    public MemoryCapability memoryCapability(LongTermMemory longTermMemory, ToolRegistry toolRegistry) {
        return new MemoryCapability(longTermMemory, toolRegistry);
    }

    @Bean
    public OverlayCapability overlayCapability(JozefProperties props, EventBus eventBus) {
        return new OverlayCapability(props.overlay(), eventBus);
    }

    @Bean
    public TrayCapability trayCapability(JozefProperties props, EventBus eventBus) {
        return new TrayCapability(props.tray(), eventBus);
    }

    @Bean
    public ProactiveAgentService proactiveAgentService(AgentRuntime agentRuntime,
                                                       SpeechCapability speechCapability,
                                                       EventBus eventBus) {
        return new ProactiveAgentService(agentRuntime, speechCapability, eventBus);
    }

    @Bean
    public ProactiveCapability proactiveCapability(JozefProperties props,
                                                   ToolRegistry toolRegistry,
                                                   ProactiveAgentService proactiveAgentService,
                                                   GoogleCapability googleCapability) {
        return new ProactiveCapability(props.proactive(), toolRegistry, proactiveAgentService, googleCapability);
    }

    @Bean
    public PlanningCapability planningCapability(JozefProperties props,
                                                 LLMCapability llmCapability,
                                                 ToolRegistry toolRegistry,
                                                 PermissionManager permissionManager,
                                                 ConfirmationService confirmationService,
                                                 PlanStore planStore) {
        return new PlanningCapability(props.planning(), llmCapability.provider(), toolRegistry,
                permissionManager, confirmationService, planStore);
    }

    @Bean
    public PlanStore planStore(JozefProperties props) {
        return new FilePlanStore(props.planning().file());
    }

    @Bean
    public TelegramCapability telegramCapability(JozefProperties props, AgentRuntime agentRuntime) {
        return new TelegramCapability(props.telegram(), agentRuntime);
    }

    @Bean
    public AssistantCapability assistantCapability(LongTermMemory longTermMemory,
                                                   GoogleCapability googleCapability,
                                                   ProactiveCapability proactiveCapability,
                                                   ToolRegistry toolRegistry) {
        return new AssistantCapability(longTermMemory, googleCapability, proactiveCapability, toolRegistry);
    }

    @Bean
    public com.jozef.assistant.WeatherAction weatherAction(ToolRegistry toolRegistry,
                                                           @Value("${jozef.weather.city:}") String city) {
        com.jozef.assistant.WeatherAction weather = new com.jozef.assistant.WeatherAction(city);
        toolRegistry.register(weather);
        return weather;
    }
}
