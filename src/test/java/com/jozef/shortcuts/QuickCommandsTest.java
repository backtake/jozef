package com.jozef.shortcuts;

import com.jozef.automation.apps.AppMatcher;
import com.jozef.automation.apps.InstalledApp;
import com.jozef.automation.apps.InstalledAppsCatalog;
import com.jozef.automation.launch.Launcher;
import com.jozef.core.agent.FastPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class QuickCommandsTest {

    @TempDir
    Path dir;

    private final List<String> opened = new ArrayList<>();
    private ShortcutStore store;
    private QuickCommands quick;
    private Launcher launcher;

    private final InstalledAppsCatalog catalog = InstalledAppsCatalog.of(List.of(
            new InstalledApp("Spotify", "SpotifyAB.SpotifyMusic_zpdnekdrzrea0!Spotify"),
            new InstalledApp("Discord", "com.squirrel.Discord.Discord"),
            new InstalledApp("Brave", "Brave"),
            new InstalledApp("Word", "Microsoft.Office.WINWORD.EXE.15"),
            new InstalledApp("Eksplorator plików", "Microsoft.Windows.Explorer"),
            new InstalledApp("Visual Studio Code", "Microsoft.VisualStudioCode"),
            new InstalledApp("Visual Studio 2022", "VisualStudio.17"),
            new InstalledApp("Uninstall Spotify", "x-uninstall")));

    @BeforeEach
    void setUp() {
        store = new ShortcutStore(dir.resolve("shortcuts.yml").toString());
        launcher = new Launcher(catalog, new Launcher.SystemOpener() {
            @Override public void open(String target) { opened.add(target); }
            @Override public void openWith(String exe, String url) { opened.add(exe + " " + url); }
        }, null);
        quick = new QuickCommands(store, catalog, launcher);
    }

    @Test
    void opensDefaultWebsiteShortcutsInstantly() {
        Optional<FastPath.Handled> handled = quick.tryHandle("Józef, otwórz YouTube");

        assertThat(handled).isPresent();
        assertThat(handled.get().reply()).isEqualTo("Otwieram YouTube.");
        assertThat(opened).containsExactly("https://www.youtube.com");
    }

    @Test
    void understandsPolishAliasesAndInflection() {
        quick.tryHandle("otwórz pocztę");
        quick.tryHandle("włącz jutuba");
        assertThat(opened).containsExactly("https://mail.google.com", "https://www.youtube.com");
    }

    @Test
    void launchesInstalledAppsByExactName() {
        Optional<FastPath.Handled> handled = quick.tryHandle("uruchom Spotify");

        assertThat(handled.get().reply()).isEqualTo("Uruchamiam Spotify.");
        assertThat(opened).containsExactly("shell:AppsFolder\\SpotifyAB.SpotifyMusic_zpdnekdrzrea0!Spotify");
    }

    @Test
    void opensSpokenDomains() {
        quick.tryHandle("wejdź na allegro.pl");
        quick.tryHandle("otwórz wp kropka pl");
        assertThat(opened).containsExactly("https://allegro.pl", "https://wp.pl");
    }

    @Test
    void leavesAnythingUncertainToTheModel() {
        assertThat(quick.tryHandle("otwórz Visual Studio")).isEmpty();          // two candidates
        assertThat(quick.tryHandle("włącz muzykę do pracy")).isEmpty();          // not a shortcut or app
        assertThat(quick.tryHandle("otwórz YouTube i puść coś śmiesznego")).isEmpty(); // compound
        assertThat(quick.tryHandle("co mam jutro w kalendarzu")).isEmpty();       // not a command
        assertThat(opened).isEmpty();
    }

    @Test
    void userShortcutsWinAndPersist() throws Exception {
        store.put(new Shortcut("poczta firmowa", List.of("outlook"), "https://outlook.office.com", null));

        quick.tryHandle("otwórz pocztę firmową");
        quick.tryHandle("odpal outlook");

        assertThat(opened).containsExactly("https://outlook.office.com", "https://outlook.office.com");
        assertThat(Files.readString(dir.resolve("shortcuts.yml"))).contains("outlook.office.com");
        assertThat(new ShortcutStore(dir.resolve("shortcuts.yml").toString()).find("outlook")).isPresent();
    }

    @Test
    void brokenShortcutsFileIsNeverOverwritten() throws Exception {
        Path file = dir.resolve("broken.yml");
        Files.writeString(file, "shortcuts: [ {name: x, url: ");
        ShortcutStore broken = new ShortcutStore(file.toString());

        assertThat(broken.all()).isEmpty();
        assertThat(Files.readString(file)).isEqualTo("shortcuts: [ {name: x, url: ");
    }

    @Test
    void launcherRejectsNonWebSchemesAndNetworkPaths() {
        assertThat(launcher.openUrl("file:///C:/Windows/System32/cmd.exe").success()).isFalse();
        assertThat(launcher.openUrl("javascript:alert(1)").success()).isFalse();
        assertThat(launcher.openApp("\\\\evil-server\\share\\run.exe").success()).isFalse();
        assertThat(opened).isEmpty();
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
