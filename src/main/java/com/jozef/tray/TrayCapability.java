package com.jozef.tray;

import com.jozef.configuration.TrayProperties;
import com.jozef.core.capabilities.Capability;
import com.jozef.core.events.CoreEvents;
import com.jozef.core.events.EventBus;
import lombok.extern.slf4j.Slf4j;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;

/**
 * System presence: tray icon + menu, Windows startup registration, and a global
 * hotkey. Each part degrades gracefully when its environment is unavailable.
 */
@Slf4j
public class TrayCapability implements Capability {

    private final TrayProperties props;
    private final EventBus eventBus;

    private TrayIcon trayIcon;
    private GlobalHotkeyService hotkey;

    public TrayCapability(TrayProperties props, EventBus eventBus) {
        this.props = props;
        this.eventBus = eventBus;
    }

    @Override
    public String name() {
        return "tray";
    }

    @Override
    public int priority() {
        return 70;
    }

    @Override
    public void initialize() {
        if (!props.enabled()) {
            log.info("Tray capability disabled by configuration.");
            return;
        }

        // Autostart works headless too (it's just a registry key).
        if (props.autostart()) {
            enableAutostart();
        }

        // Tray icon needs a real desktop.
        if (!GraphicsEnvironment.isHeadless() && SystemTray.isSupported()) {
            SwingUtilities.invokeLater(this::addTrayIcon);
        } else {
            log.warn("System tray not available — running without tray icon.");
        }

        // Global hotkey is optional and non-fatal.
        if (props.hotkey() != null && !props.hotkey().isBlank()) {
            registerHotkey();
        }

        log.info("Tray capability initialized (tray={}, autostart={}, hotkey={}).",
                SystemTray.isSupported(), props.autostart(),
                hotkey != null && hotkey.isRegistered());
    }

    private void enableAutostart() {
        String command = WindowsAutostartManager.currentJarCommand();
        if (command == null) {
            log.warn("Autostart requested but no boot jar found "
                    + "(build first, then run the jar from the project directory).");
            return;
        }
        new WindowsAutostartManager().enable("Jozef", command);
    }

    private void registerHotkey() {
        try {
            HotkeySpec spec = NativeGlobalHotkeyService.parse(props.hotkey());
            hotkey = new NativeGlobalHotkeyService(spec, () -> {
                log.info("Global hotkey pressed: {}", props.hotkey());
                eventBus.publish(new CoreEvents.HotkeyPressed());
            });
            hotkey.register();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid hotkey '{}': {}", props.hotkey(), e.getMessage());
        }
    }

    private void addTrayIcon() {
        try {
            SystemTray tray = SystemTray.getSystemTray();

            PopupMenu menu = new PopupMenu();
            MenuItem status = new MenuItem("Jozef — aktywny");
            status.setEnabled(false);
            menu.add(status);
            menu.addSeparator();
            MenuItem write = new MenuItem("Napisz polecenie…  (" + (props.hotkey() == null ? "" : props.hotkey()) + ")");
            write.addActionListener(e -> eventBus.publish(new CoreEvents.UiRequest(CoreEvents.UiRequest.Kind.OPEN_COMMAND_WINDOW)));
            menu.add(write);
            MenuItem listening = new MenuItem("Wstrzymaj słuchanie");
            listening.addActionListener(e -> eventBus.publish(new CoreEvents.UiRequest(CoreEvents.UiRequest.Kind.TOGGLE_LISTENING)));
            menu.add(listening);
            eventBus.register(CoreEvents.ListeningStateChanged.class, ev -> SwingUtilities.invokeLater(() ->
                    listening.setLabel(ev.paused() ? "Wznów słuchanie" : "Wstrzymaj słuchanie")));
            WindowsAutostartManager autostart = new WindowsAutostartManager();
            java.awt.CheckboxMenuItem startWithWindows = new java.awt.CheckboxMenuItem(
                    "Uruchamiaj z Windowsem", autostart.isEnabled("Jozef"));
            startWithWindows.addItemListener(e -> new Thread(() -> {
                if (startWithWindows.getState()) {
                    enableAutostart();
                } else {
                    autostart.disable("Jozef");
                }
            }, "jozef-autostart").start());
            menu.add(startWithWindows);
            MenuItem google = new MenuItem("Połącz z Google (Gmail, Kalendarz)…");
            google.addActionListener(e -> eventBus.publish(new CoreEvents.UiRequest(CoreEvents.UiRequest.Kind.CONNECT_GOOGLE)));
            menu.add(google);
            MenuItem shortcuts = new MenuItem("Edytuj skróty (strony i programy)…");
            shortcuts.addActionListener(e -> eventBus.publish(new CoreEvents.UiRequest(CoreEvents.UiRequest.Kind.OPEN_SHORTCUTS_FILE)));
            menu.add(shortcuts);
            menu.addSeparator();
            MenuItem exit = new MenuItem("Zakończ");
            exit.addActionListener(e -> exitApplication());
            menu.add(exit);

            TrayIcon icon = new TrayIcon(createIcon(), "Jozef", menu);
            icon.setImageAutoSize(true);
            tray.add(icon);
            this.trayIcon = icon;
            // Reminders and new-mail notices also appear as Windows notifications — visible
            // when the speakers are off or listening is paused.
            eventBus.register(com.jozef.proactive.ProactiveEvent.class, ev -> {
                String text = ev.message() == null ? "" : ev.message();
                icon.displayMessage("Jozef", text.length() > 240 ? text.substring(0, 237) + "…" : text,
                        TrayIcon.MessageType.INFO);
            });
            log.info("System tray icon added.");
        } catch (Exception e) {
            log.warn("Could not add system tray icon: {}", e.getMessage());
        }
    }

    private void exitApplication() {
        log.info("Exit requested from tray menu.");
        // System.exit triggers the shutdown hook, which stops the voice loop
        // and all capabilities gracefully.
        new Thread(() -> System.exit(0), "jozef-tray-exit").start();
    }

    private Image createIcon() {
        int size = 16;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(59, 130, 246));
        g.fillOval(1, 1, size - 2, size - 2);
        g.dispose();
        return image;
    }

    @Override
    public void shutdown() {
        if (hotkey != null) {
            hotkey.stop();
        }
        if (trayIcon != null) {
            try {
                SystemTray.getSystemTray().remove(trayIcon);
            } catch (Exception ignored) {
                // tray may already be gone during JVM shutdown
            }
        }
        log.info("Tray capability shut down");
    }
}
