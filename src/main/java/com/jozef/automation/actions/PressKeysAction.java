package com.jozef.automation.actions;

import com.jozef.automation.api.DesktopAutomation;
import com.jozef.core.action.Action;
import com.jozef.core.action.ActionContext;
import com.jozef.core.action.ActionResult;
import com.jozef.core.action.ExecutionStrategy;
import com.jozef.core.tool.ToolParameters;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Presses a key combination, e.g. "ctrl+c", "alt+tab", "enter".
 *
 * Keys are how typed text becomes an action (Enter, Space on a focused button,
 * Tab to a "Send" button, Win/Ctrl+Esc for Start). So the default is to ask, and
 * only a short allowlist of navigation/editing combos runs without confirmation.
 */
public class PressKeysAction implements Action {

    /** Combos (normalised: lower case, sorted keys joined with +) that are safe to press unasked. */
    private static final Set<String> SAFE_COMBOS = Set.of(
            "up", "down", "left", "right", "pageup", "pagedown", "page_up", "page_down", "home", "end",
            "esc", "escape", "f5", "f11", "backspace",
            "c+ctrl", "ctrl+v", "ctrl+x", "ctrl+z", "ctrl+y", "a+ctrl", "ctrl+f", "ctrl+t", "ctrl+tab",
            "ctrl+shift+tab", "ctrl+shift+t", "alt+tab", "ctrl+plus", "ctrl+minus", "0+ctrl", "=+ctrl", "-+ctrl",
            "alt+left", "alt+right");

    private final DesktopAutomation automation;
    private final TypingMemory typing;

    public PressKeysAction(DesktopAutomation automation) {
        this(automation, null);
    }

    public PressKeysAction(DesktopAutomation automation, TypingMemory typing) {
        this.automation = automation;
        this.typing = typing;
    }

    @Override
    public String name() {
        return "press_keys";
    }

    @Override
    public String description() {
        return "Press a key combination, e.g. ctrl+c, alt+tab, enter, escape.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return new ToolParameters()
                .string("combo", "Key combination to press", true)
                .build();
    }

    @Override
    public Set<String> requiredPermissions() {
        return Set.of("KEYBOARD_MOUSE");
    }

    @Override
    public ExecutionStrategy strategyFor(Map<String, Object> arguments) {
        return SAFE_COMBOS.contains(normalize(comboOf(arguments))) ? ExecutionStrategy.SAFE : ExecutionStrategy.CONFIRM;
    }

    static String normalize(String combo) {
        String c = combo.toLowerCase().replace(" ", "").replace("control", "ctrl");
        return String.join("+", new TreeSet<>(Arrays.asList(c.split("\\+"))));
    }

    @Override
    public String confirmationSummary(Map<String, Object> arguments) {
        StringBuilder sb = new StringBuilder("nacisnąć klawisze ").append(comboOf(arguments));
        String window = activeWindow();
        if (window != null && !window.isBlank()) {
            sb.append(" w oknie „").append(window).append("”");
        }
        String recent = typing == null ? null : typing.recentText();
        if (recent != null) {
            sb.append(" (wcześniej wpisano: „").append(recent.length() > 120 ? recent.substring(0, 117) + "…" : recent)
                    .append("”)");
        }
        return sb.toString();
    }

    private String activeWindow() {
        try {
            return automation == null ? null : automation.getActiveWindowTitle();
        } catch (Exception e) {
            return null;
        }
    }

    private static String comboOf(Map<String, Object> arguments) {
        Object raw = arguments == null ? null : arguments.get("combo");
        return raw == null ? "" : raw.toString();
    }

    @Override
    public ActionResult execute(ActionContext context) {
        String combo = context.getString("combo");
        if (combo == null || combo.isEmpty()) {
            return ActionResult.fail("No key combo provided");
        }
        automation.pressKey(combo);
        return ActionResult.ok("Pressed: " + combo);
    }
}
