package com.jozef.shortcuts;

import com.jozef.automation.actions.OpenApplicationAction;
import com.jozef.automation.actions.OpenUrlAction;
import com.jozef.automation.apps.AppMatcher;
import com.jozef.automation.apps.InstalledApp;
import com.jozef.automation.apps.InstalledAppsCatalog;
import com.jozef.automation.launch.Launcher;
import com.jozef.configuration.AgentProperties;
import com.jozef.configuration.LlmProperties;
import com.jozef.configuration.MemoryProperties;
import com.jozef.core.agent.AgentResult;
import com.jozef.core.agent.AgentRuntime;
import com.jozef.core.events.Event;
import com.jozef.core.events.EventBus;
import com.jozef.core.events.EventListener;
import com.jozef.core.security.ConversationalConfirmationService;
import com.jozef.core.security.PermissionManager;
import com.jozef.core.session.SessionManager;
import com.jozef.core.tool.ToolRegistry;
import com.jozef.llm.ChatRequest;
import com.jozef.llm.ChatResponse;
import com.jozef.llm.LLMProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Instant commands, end to end through the agent runtime and the real launch tools. */
class QuickCommandsTest {

    @TempDir
    Path dir;

    private final List<String> opened = new ArrayList<>();
    private final AtomicInteger llmCalls = new AtomicInteger();
    private boolean openerFails;
    private ShortcutStore store;
    private QuickCommands quick;
    private Launcher launcher;
    private AgentRuntime agent;

    private final InstalledAppsCatalog catalog = InstalledAppsCatalog.of(List.of(
            new InstalledApp("Spotify", "SpotifyAB.SpotifyMusic_zpdnekdrzrea0!Spotify"),
            new InstalledApp("Discord", "com.squirrel.Discord.Discord"),
            new InstalledApp("Brave", "Brave"),
            new InstalledApp("Word", "Microsoft.Office.WINWORD.EXE.15"),
            new InstalledApp("Paint", "Microsoft.Paint"),
            new InstalledApp("Eksplorator plików", "Microsoft.Windows.Explorer"),
            new InstalledApp("Visual Studio Code", "Microsoft.VisualStudioCode"),
            new InstalledApp("Visual Studio 2022", "VisualStudio.17"),
            new InstalledApp("Uninstall Spotify", "x-uninstall")));

    @BeforeEach
    void setUp() {
        store = new ShortcutStore(dir.resolve("shortcuts.yml").toString());
        launcher = new Launcher(catalog, new Launcher.SystemOpener() {
            @Override public void open(String target) {
                if (openerFails) throw new IllegalStateException("no such app");
                opened.add(target);
            }
            @Override public void openWith(String exe, String url) { opened.add(exe + " " + url); }
        }, null);
        quick = new QuickCommands(store, catalog);

        ToolRegistry tools = new ToolRegistry();
        tools.register(new OpenApplicationAction(launcher, store));
        tools.register(new OpenUrlAction(launcher, store));
        LLMProvider llm = new LLMProvider() {
            @Override public String name() { return "counting"; }
            @Override public ChatResponse chat(ChatRequest request) {
                llmCalls.incrementAndGet();
                return new ChatResponse("(model)", List.of());
            }
        };
        EventBus bus = new EventBus() {
            @Override public void publish(Event event) {}
            @Override public <E extends Event> void register(Class<E> type, EventListener<E> l) {}
        };
        agent = new AgentRuntime(llm, tools, () -> "sys", bus,
                new SessionManager(dir.resolve("session.json").toString()), new PermissionManager(),
                new ConversationalConfirmationService(), new AgentProperties(5, 10),
                new LlmProperties("x", "m", null, null, 0.2, 100, null), null, new MemoryProperties(20, 5, null, null));
        agent.addFastPath(quick);
    }

    private AgentResult say(String text) {
        return agent.process(text);
    }

    @Test
    void opensDefaultWebsiteShortcutsInstantly() {
        AgentResult result = say("Józef, otwórz YouTube");

        assertThat(result.text()).isEqualTo("Otwieram YouTube.");
        assertThat(opened).containsExactly("https://www.youtube.com");
        assertThat(llmCalls).hasValue(0);
    }

    @Test
    void understandsPolishAliasesAndInflection() {
        say("otwórz pocztę");
        say("włącz jutuba");
        assertThat(opened).containsExactly("https://mail.google.com", "https://www.youtube.com");
        assertThat(llmCalls).hasValue(0);
    }

    @Test
    void launchesInstalledAppsByExactName() {
        AgentResult result = say("uruchom Spotify");

        assertThat(result.text()).isEqualTo("Uruchamiam Spotify.");
        assertThat(opened).containsExactly("shell:AppsFolder\\SpotifyAB.SpotifyMusic_zpdnekdrzrea0!Spotify");
    }

    @Test
    void opensSpokenDomainsButNotFiles() {
        say("wejdź na allegro.pl");
        say("otwórz wp kropka pl");
        assertThat(opened).containsExactly("https://allegro.pl", "https://wp.pl");

        assertThat(quick.resolve("otwórz raport.pdf")).isEmpty();
    }

    @Test
    void leavesAnythingUncertainToTheModel() {
        assertThat(quick.resolve("otwórz Visual Studio")).isEmpty();          // two candidates
        assertThat(quick.resolve("włącz muzykę do pracy")).isEmpty();          // not a shortcut or app
        assertThat(quick.resolve("otwórz YouTube i puść coś śmiesznego")).isEmpty(); // compound
        assertThat(quick.resolve("co mam jutro w kalendarzu")).isEmpty();       // not a command
        assertThat(quick.resolve("pokaż kalendarz")).isEmpty();                 // information request
        assertThat(quick.resolve("otwórz pa")).isEmpty();                       // too short → Paint?
        assertThat(quick.resolve("otwórz to")).isEmpty();
    }

    @Test
    void failedLaunchHandsOverToTheModel() {
        openerFails = true;
        AgentResult result = say("uruchom Discord");

        assertThat(llmCalls).hasValue(1);
        assertThat(result.text()).isEqualTo("(model)");
    }

    @Test
    void userShortcutsWinAndPersist() throws Exception {
        store.put(new Shortcut("poczta firmowa", List.of("outlook"), "https://outlook.office.com", null));

        say("otwórz pocztę firmową");
        say("odpal outlook");

        assertThat(opened).containsExactly("https://outlook.office.com", "https://outlook.office.com");
        assertThat(Files.readString(dir.resolve("shortcuts.yml"))).contains("outlook.office.com");
        assertThat(new ShortcutStore(dir.resolve("shortcuts.yml").toString()).find("outlook")).isPresent();
    }

    @Test
    void handWrittenPathShortcutsWorkButModelSuppliedPathsDoNot() {
        store.put(new Shortcut("Gra", List.of(), null, "C:\\Games\\Gra\\gra.exe"));
        say("uruchom grę");
        assertThat(opened).containsExactly("C:\\Games\\Gra\\gra.exe");

        assertThat(launcher.openApp("C:\\Users\\x\\Downloads\\evil.bat").success()).isFalse();
        assertThat(launcher.openApp("shell:startup").success()).isFalse();
        assertThat(launcher.openApp("\\\\evil-server\\share\\run.exe").success()).isFalse();
        assertThat(launcher.openUrl("file:///C:/Windows/System32/cmd.exe").success()).isFalse();
        assertThat(launcher.openUrl("javascript:alert(1)").success()).isFalse();
    }

    @Test
    void savingShortcutsThroughTheModelNeedsConfirmationAndCannotPointAtFiles() {
        var save = ShortcutTools.create(store).get(0);
        assertThat(save.strategyFor(Map.of())).isNotEqualTo(com.jozef.core.action.ExecutionStrategy.SAFE);

        assertThat(ShortcutTools.validate(Map.of("name", "YouTube", "app", "C:\\Users\\x\\Downloads\\x.bat")).error())
                .contains("installed program");
        assertThat(ShortcutTools.validate(Map.of("name", "Notes", "app", "notes.lnk")).error()).isNotNull();
        assertThat(ShortcutTools.validate(Map.of("name", "Muzyka", "app", "spotify:")).error()).isNull();
        assertThat(ShortcutTools.validate(Map.of("name", "Faktury", "url", "faktury.example.pl")).shortcut().url())
                .isEqualTo("https://faktury.example.pl");

        // The confirmation tells the user that a default would be replaced.
        String summary = save.confirmationSummary(Map.of("name", "Gmail", "url", "https://gmail-login.example"));
        assertThat(summary).contains("ZASTĄPI").contains("mail.google.com").contains("gmail-login.example");
    }

    @Test
    void brokenShortcutsFileIsNeverOverwritten() throws Exception {
        Path file = dir.resolve("broken.yml");
        Files.writeString(file, "shortcuts: [ {name: x, url: ");
        ShortcutStore broken = new ShortcutStore(file.toString());

        assertThat(broken.all()).isEmpty();
        assertThatThrownBy(() -> broken.put(Shortcut.website("Nowy", "https://example.com")))
                .hasMessageContaining("syntax error");
        assertThat(Files.readString(file)).isEqualTo("shortcuts: [ {name: x, url: ");
    }

    @Test
    void handEditsArePickedUpWithoutRestart() throws Exception {
        Path file = dir.resolve("edited.yml");
        ShortcutStore edited = new ShortcutStore(file.toString());
        assertThat(edited.find("faktury")).isEmpty();

        Files.writeString(file, "shortcuts:\n  - name: Faktury\n    url: https://faktury.example.pl\n");
        Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5_000));

        assertThat(edited.find("faktury")).map(Shortcut::url).contains("https://faktury.example.pl");
    }

    @Test
    void appMatcherIgnoresUninstallersAndHandlesAccusative() {
        assertThat(AppMatcher.best(catalog.apps(), "spotify")).map(InstalledApp::name).contains("Spotify");
        assertThat(AppMatcher.best(catalog.apps(), "discorda")).map(InstalledApp::name).contains("Discord");
        assertThat(AppMatcher.best(catalog.apps(), "worda")).map(InstalledApp::name).contains("Word");
        assertThat(AppMatcher.best(catalog.apps(), "eksplorator")).map(InstalledApp::name).contains("Eksplorator plików");
        assertThat(AppMatcher.best(catalog.apps(), "photoshop")).isEmpty();
    }

    @Test
    void ambiguousAppIsReportedWithCandidates() {
        Launcher.LaunchResult result = launcher.openApp("visual studio");
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("Visual Studio Code").contains("Visual Studio 2022");
    }
}
