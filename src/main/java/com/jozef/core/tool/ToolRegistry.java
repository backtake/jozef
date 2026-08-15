package com.jozef.core.tool;

import com.jozef.core.action.Action;
import com.jozef.core.action.ActionContext;
import com.jozef.core.action.ActionResult;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The central registry of tools/actions available to Jozef.
 *
 * This is what the Agent consults to:
 *   - expose tool definitions to the LLM ({@link #definitions()})
 *   - look up and execute a tool by name ({@link #execute(String, Map)})
 *
 * Capabilities register their actions here during initialization.
 */
@Slf4j
public class ToolRegistry {

    private final Map<String, Action> actions = new ConcurrentHashMap<>();

    public void register(Action action) {
        if (action == null) {
            throw new IllegalArgumentException("Cannot register a null action");
        }
        Action previous = actions.put(action.name(), action);
        if (previous != null) {
            log.warn("Tool '{}' was re-registered (replacing previous implementation)", action.name());
        } else {
            log.info("Registered tool: {}", action.name());
        }
    }

    public void registerAll(Iterable<Action> actions) {
        actions.forEach(this::register);
    }

    public Optional<Action> find(String name) {
        return Optional.ofNullable(actions.get(name));
    }

    public boolean knows(String name) {
        return actions.containsKey(name);
    }

    public ActionResult execute(String name, Map<String, Object> arguments) {
        Action action = actions.get(name);
        if (action == null) {
            log.warn("Unknown tool requested: {}", name);
            return ActionResult.fail("Unknown tool: " + name);
        }

        log.debug("Executing tool: {} with {}", name, arguments);
        try {
            return action.execute(ActionContext.of(arguments));
        } catch (Exception e) {
            log.error("Tool '{}' threw an exception: {}", name, e.getMessage(), e);
            return ActionResult.fail("Tool failed: " + e.getMessage());
        }
    }

    /** Tool definitions for the LLM, sorted by name for determinism. */
    public List<ToolDefinition> definitions() {
        return actions.values().stream()
                .map(ToolDefinition::from)
                .sorted(Comparator.comparing(ToolDefinition::name))
                .toList();
    }

    public Set<String> names() {
        return Collections.unmodifiableSet(actions.keySet());
    }

    public int size() {
        return actions.size();
    }
}
