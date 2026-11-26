package com.jozef.automation.actions;

import com.jozef.core.action.Action;
import com.jozef.core.action.ActionContext;
import com.jozef.core.action.ActionResult;
import com.jozef.core.tool.ToolParameters;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Media keys: play/pause, next/previous track, volume, mute — works with Spotify,
 * YouTube in the browser and most players. AWT Robot has no media key codes, so the
 * virtual keys are pressed through user32 keybd_event (fixed, whitelisted key codes only).
 */
public class MediaControlAction implements Action {

    /** Virtual-key codes (VK_MEDIA_*, VK_VOLUME_*). */
    private static final Map<String, Integer> KEYS = Map.of(
            "play_pause", 0xB3,
            "next", 0xB0,
            "previous", 0xB1,
            "stop", 0xB2,
            "volume_up", 0xAF,
            "volume_down", 0xAE,
            "mute", 0xAD);

    /** Sends one virtual key; replaceable in tests. */
    public interface KeySender {
        void send(int virtualKey, int times) throws Exception;
    }

    private final KeySender sender;

    public MediaControlAction() {
        this(MediaControlAction::sendVirtualKey);
    }

    public MediaControlAction(KeySender sender) {
        this.sender = sender;
    }

    @Override
    public String name() {
        return "media_control";
    }

    @Override
    public String description() {
        return "Control music/video playback and volume: play_pause, next, previous, stop, volume_up, "
                + "volume_down, mute. Works with Spotify, YouTube and most players.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return new ToolParameters()
                .string("action", "play_pause | next | previous | stop | volume_up | volume_down | mute", true)
                .integer("steps", "For volume: how many steps (1 step ≈ 2%, default 5)", false)
                .build();
    }

    @Override
    public Set<String> requiredPermissions() {
        return Set.of("KEYBOARD_MOUSE");
    }

    @Override
    public ActionResult execute(ActionContext context) {
        String action = context.getString("action");
        Integer vk = action == null ? null : KEYS.get(action.trim().toLowerCase());
        if (vk == null) {
            return ActionResult.fail("Unknown media action: " + action + ". Use one of " + KEYS.keySet());
        }
        int steps = 1;
        if (action.startsWith("volume_")) {
            steps = context.get("steps") instanceof Number n ? Math.max(1, Math.min(25, n.intValue())) : 5;
        }
        try {
            sender.send(vk, steps);
            return ActionResult.ok("Media: " + action + (steps > 1 ? " ×" + steps : ""));
        } catch (Exception e) {
            return ActionResult.fail("Media key failed: " + e.getMessage());
        }
    }

    /**
     * Presses the real virtual key (key down + key up) through user32 keybd_event.
     * NOT WScript.Shell.SendKeys([char]n): that types a character, and on the Polish
     * layout 0xB3/0xB2 are the characters ³/² — "pauza" would type "3" into the focused window.
     * Only integer key codes from the table above reach the script — no user text.
     */
    private static void sendVirtualKey(int virtualKey, int times) throws Exception {
        Process p = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand",
                encodedCommand(script(virtualKey, times)))
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        if (!p.waitFor(10, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IllegalStateException("timed out");
        }
        if (p.exitValue() != 0) {
            throw new IllegalStateException("exit " + p.exitValue() + ": " + out.trim());
        }
    }

    public static String script(int virtualKey, int times) {
        return "Add-Type -Namespace JozefKeys -Name Native -MemberDefinition "
                + "'[DllImport(\"user32.dll\")] public static extern void keybd_event(byte vk, byte scan, uint flags, System.UIntPtr extra);'; "
                + "for ($i = 0; $i -lt " + times + "; $i++) { "
                + "[JozefKeys.Native]::keybd_event(" + virtualKey + ", 0, 1, [System.UIntPtr]::Zero); "   // down, EXTENDEDKEY
                + "[JozefKeys.Native]::keybd_event(" + virtualKey + ", 0, 3, [System.UIntPtr]::Zero) }";  // up
    }

    /** -EncodedCommand: Base64 of UTF-16LE — survives the quotes Java's Windows command line would mangle. */
    public static String encodedCommand(String script) {
        return java.util.Base64.getEncoder().encodeToString(script.getBytes(java.nio.charset.StandardCharsets.UTF_16LE));
    }
}
