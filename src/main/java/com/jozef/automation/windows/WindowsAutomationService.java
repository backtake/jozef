package com.jozef.automation.windows;

import com.jozef.automation.api.DesktopAutomation;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Windows desktop automation built on Java AWT {@link Robot}.
 *
 * Zero native dependencies: mouse/keyboard via Robot, clipboard via AWT,
 * process/window control via {@code cmd} / PowerShell.
 */
@Slf4j
public class WindowsAutomationService implements DesktopAutomation {

    private final Robot robot;
    private final ScheduledExecutorService clipboardRestore =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "clipboard-restore");
                t.setDaemon(true);
                return t;
            });

    public WindowsAutomationService() {
        try {
            this.robot = new Robot();
            robot.setAutoDelay(40);
        } catch (AWTException e) {
            throw new IllegalStateException("Cannot create AWT Robot for automation", e);
        }
    }

    @Override
    public String platform() {
        return "windows";
    }

    @Override
    public void launchApplication(String nameOrPath) {
        try {
            String cmd = switch (nameOrPath.toLowerCase().trim()) {
                case "chrome", "google chrome" -> "start chrome";
                case "firefox" -> "start firefox";
                case "edge", "msedge" -> "start msedge";
                case "notepad", "notatnik" -> "start notepad";
                case "calculator", "kalkulator" -> "calc";
                case "explorer", "file explorer", "eksplorator" -> "explorer";
                case "cmd", "terminal", "command prompt" -> "start cmd";
                case "powershell" -> "start powershell";
                case "task manager", "menedżer zadań" -> "taskmgr";
                case "settings", "ustawienia" -> "start ms-settings:";
                case "spotify" -> "start spotify:";
                default -> {
                    String safe = ShellArgs.requireSafeShellArgument(nameOrPath, "Application name");
                    yield "start \"\" \"" + safe + "\"";
                }
            };
            Runtime.getRuntime().exec(new String[]{"cmd", "/c", cmd});
            log.info("Launched application: {}", nameOrPath);
        } catch (Exception e) {
            log.error("Failed to launch application: {}", nameOrPath, e);
            throw new RuntimeException("Failed to launch: " + nameOrPath, e);
        }
    }

    @Override
    public void closeApplication(String nameOrProcess) {
        String process = switch (nameOrProcess.toLowerCase().trim()) {
            case "chrome", "google chrome" -> "chrome.exe";
            case "firefox" -> "firefox.exe";
            case "edge", "msedge" -> "msedge.exe";
            case "notepad", "notatnik" -> "notepad.exe";
            case "calculator", "kalkulator" -> "CalculatorApp.exe";
            case "explorer", "file explorer" -> "explorer.exe";
            default -> nameOrProcess;
        };
        try {
            Runtime.getRuntime().exec(new String[]{"taskkill", "/IM", process, "/F"});
            log.info("Requested close of: {} ({})", nameOrProcess, process);
        } catch (Exception e) {
            log.error("Failed to close application: {}", nameOrProcess, e);
            throw new RuntimeException("Failed to close: " + nameOrProcess, e);
        }
    }

    @Override
    public void focusWindow(String titleOrProcess) {
        // Best-effort via PowerShell: bring the process's main window to front.
        try {
            String safe = ShellArgs.escapePowerShellSingleQuoted(titleOrProcess);
            String script = "Get-Process | Where-Object { $_.MainWindowTitle -like '*"
                    + safe + "*' } | ForEach-Object { "
                    + "$sig = '[DllImport(\"user32.dll\")] public static extern bool SetForegroundWindow(IntPtr hWnd);'; "
                    + "Add-Type -MemberDefinition $sig -Name W -Namespace N; "
                    + "[N.W]::SetForegroundWindow($_.MainWindowHandle) }";
            Runtime.getRuntime().exec(new String[]{"powershell", "-NoProfile", "-Command", script});
        } catch (Exception e) {
            log.warn("focusWindow failed for {}: {}", titleOrProcess, e.getMessage());
        }
    }

    @Override
    public List<String> listWindows() {
        List<String> titles = new ArrayList<>();
        try {
            Process p = Runtime.getRuntime().exec(new String[]{
                    "powershell", "-NoProfile", "-Command",
                    "Get-Process | Where-Object { $_.MainWindowTitle -ne '' } | Select-Object -ExpandProperty MainWindowTitle"
            });
            try (var reader = p.inputReader()) {
                reader.lines().filter(s -> !s.isBlank()).forEach(titles::add);
            }
        } catch (Exception e) {
            log.warn("listWindows failed: {}", e.getMessage());
        }
        return titles;
    }

    @Override
    public String getActiveWindowTitle() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{
                    "powershell", "-NoProfile", "-Command",
                    "(Get-Process | Where-Object { $_.MainWindowTitle -ne '' } | "
                            + "Sort-Object StartTime -Descending | Select-Object -First 1).MainWindowTitle"
            });
            try (var reader = p.inputReader()) {
                return reader.lines().findFirst().orElse("");
            }
        } catch (Exception e) {
            log.warn("getActiveWindowTitle failed: {}", e.getMessage());
            return "";
        }
    }

    @Override
    public void click(int x, int y) {
        robot.mouseMove(x, y);
        robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
    }

    @Override
    public void doubleClick(int x, int y) {
        click(x, y);
        robot.delay(60);
        click(x, y);
    }

    @Override
    public void moveMouse(int x, int y) {
        robot.mouseMove(x, y);
    }

    @Override
    public void scroll(int amount) {
        robot.mouseWheel(amount);
    }

    @Override
    public void typeText(String text) {
        String previous = readClipboard();
        writeClipboard(text);
        pressKey("ctrl+v");
        // Best-effort restore of the user's clipboard (shared daemon executor).
        clipboardRestore.schedule(() -> writeClipboard(previous), 400, TimeUnit.MILLISECONDS);
    }

    @Override
    public void pressKey(String keyCombo) {
        String[] parts = keyCombo.trim().toLowerCase().split("\\+");
        List<Integer> modifiers = new ArrayList<>();
        int mainKey = KeyEvent.VK_UNDEFINED;

        for (String part : parts) {
            int code = keyCode(part.trim());
            if (code == KeyEvent.VK_CONTROL || code == KeyEvent.VK_ALT
                    || code == KeyEvent.VK_SHIFT || code == KeyEvent.VK_WINDOWS) {
                modifiers.add(code);
            } else {
                mainKey = code;
            }
        }

        for (int mod : modifiers) robot.keyPress(mod);
        if (mainKey != KeyEvent.VK_UNDEFINED) {
            robot.keyPress(mainKey);
            robot.keyRelease(mainKey);
        }
        for (int i = modifiers.size() - 1; i >= 0; i--) robot.keyRelease(modifiers.get(i));
    }

    @Override
    public void hotkey(String... keys) {
        for (String key : keys) robot.keyPress(keyCode(key.trim().toLowerCase()));
        for (int i = keys.length - 1; i >= 0; i--) robot.keyRelease(keyCode(keys[i].trim().toLowerCase()));
    }

    @Override
    public String readClipboard() {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            Transferable contents = clipboard.getContents(null);
            if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                Object data = contents.getTransferData(DataFlavor.stringFlavor);
                return data == null ? "" : data.toString();
            }
            return "";
        } catch (Exception e) {
            log.warn("readClipboard failed: {}", e.getMessage());
            return "";
        }
    }

    @Override
    public void writeClipboard(String text) {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new StringSelection(text), null);
        } catch (Exception e) {
            log.warn("writeClipboard failed: {}", e.getMessage());
        }
    }

    @Override
    public String takeScreenshot() {
        try {
            Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            BufferedImage screenshot = robot.createScreenCapture(screenRect);
            String filename = "screenshot_" + Instant.now().toString().replace(":", "-") + ".png";
            ImageIO.write(screenshot, "png", new File(filename));
            return filename;
        } catch (Exception e) {
            log.error("Screenshot failed", e);
            throw new RuntimeException("Screenshot failed: " + e.getMessage(), e);
        }
    }

    private int keyCode(String key) {
        return switch (key) {
            case "enter", "return" -> KeyEvent.VK_ENTER;
            case "esc", "escape" -> KeyEvent.VK_ESCAPE;
            case "tab" -> KeyEvent.VK_TAB;
            case "space", "spacebar" -> KeyEvent.VK_SPACE;
            case "backspace" -> KeyEvent.VK_BACK_SPACE;
            case "delete", "del" -> KeyEvent.VK_DELETE;
            case "up" -> KeyEvent.VK_UP;
            case "down" -> KeyEvent.VK_DOWN;
            case "left" -> KeyEvent.VK_LEFT;
            case "right" -> KeyEvent.VK_RIGHT;
            case "home" -> KeyEvent.VK_HOME;
            case "end" -> KeyEvent.VK_END;
            case "pageup", "page_up" -> KeyEvent.VK_PAGE_UP;
            case "pagedown", "page_down" -> KeyEvent.VK_PAGE_DOWN;
            case "ctrl", "control" -> KeyEvent.VK_CONTROL;
            case "alt" -> KeyEvent.VK_ALT;
            case "shift" -> KeyEvent.VK_SHIFT;
            case "win", "windows", "meta" -> KeyEvent.VK_WINDOWS;
            case "f1" -> KeyEvent.VK_F1;
            case "f2" -> KeyEvent.VK_F2;
            case "f3" -> KeyEvent.VK_F3;
            case "f4" -> KeyEvent.VK_F4;
            case "f5" -> KeyEvent.VK_F5;
            case "f6" -> KeyEvent.VK_F6;
            case "f7" -> KeyEvent.VK_F7;
            case "f8" -> KeyEvent.VK_F8;
            case "f9" -> KeyEvent.VK_F9;
            case "f10" -> KeyEvent.VK_F10;
            case "f11" -> KeyEvent.VK_F11;
            case "f12" -> KeyEvent.VK_F12;
            default -> {
                if (key.length() == 1) {
                    yield KeyEvent.getExtendedKeyCodeForChar(key.charAt(0));
                }
                yield KeyEvent.VK_UNDEFINED;
            }
        };
    }
}
