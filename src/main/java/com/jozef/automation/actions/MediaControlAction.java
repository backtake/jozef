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
 * virtual keys are sent through WScript.Shell (fixed, whitelisted key codes only).
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
        this(MediaControlAction::sendWithWScript);
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

    private static void sendWithWScript(int virtualKey, int times) throws Exception {
        // Only integer key codes from the table above reach the script — no user text.
        String script = "$w = New-Object -ComObject WScript.Shell; for ($i = 0; $i -lt " + times + "; $i++) { "
                + "$w.SendKeys([string][char]" + virtualKey + ") }";
        Process p = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", script)
                .redirectErrorStream(true).start();
        p.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
        if (!p.waitFor(10, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IllegalStateException("timed out");
        }
    }
}
