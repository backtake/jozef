package com.jozef.core.builtin;

import com.jozef.core.action.Action;
import com.jozef.core.action.ActionContext;
import com.jozef.core.action.ActionResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * "Józef, co u ciebie działa?" — a plain report of which brain, voice and
 * connections are active, so problems (no credits, no Google login, no phone
 * bot) are visible without reading logs.
 */
public class StatusAction implements Action {

    private final Map<String, Supplier<String>> parts = new LinkedHashMap<>();

    /** Adds a line; the supplier is evaluated each time the status is asked for. */
    public StatusAction add(String label, Supplier<String> value) {
        parts.put(label, value);
        return this;
    }

    @Override
    public String name() {
        return "jozef_status";
    }

    @Override
    public String description() {
        return "Report Jozef's own state: which language model and voice are in use, whether Google, "
                + "the phone (Telegram) and the microphone are connected, how many shortcuts and apps it knows. "
                + "Use when the user asks what works, why something fails, or how Jozef is set up.";
    }

    @Override
    public ActionResult execute(ActionContext context) {
        StringBuilder sb = new StringBuilder();
        parts.forEach((label, value) -> {
            String v;
            try {
                v = value.get();
            } catch (Exception e) {
                v = "nieznany (" + e.getMessage() + ")";
            }
            sb.append(label).append(": ").append(v).append("\n");
        });
        return ActionResult.ok(sb.toString().trim());
    }
}
