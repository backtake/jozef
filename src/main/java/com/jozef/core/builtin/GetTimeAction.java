package com.jozef.core.builtin;

import com.jozef.core.action.Action;
import com.jozef.core.action.ActionContext;
import com.jozef.core.action.ActionResult;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Built-in tool: current local date and time.
 * Enables "Jozef, what time is it?" without any external integration.
 */
public class GetTimeAction implements Action {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public String name() {
        return "get_current_time";
    }

    @Override
    public String description() {
        return "Get the current local date and time.";
    }

    @Override
    public ActionResult execute(ActionContext context) {
        String now = LocalDateTime.now().format(FORMAT);
        return ActionResult.ok("Current time is " + now, Map.of("time", now));
    }
}
