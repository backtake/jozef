package com.jozef.core.plugins;

import com.jozef.configuration.PluginsProperties;
import com.jozef.core.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Discovers and loads plugins.
 *
 * Discovery strategies:
 * 1. Spring beans implementing Plugin (auto-discovered via DI)
 * 2. Java ServiceLoader (META-INF/services)
 * 3. External directory scanning (for hot-pluggable jars)
 */
@Slf4j
public class PluginLoader {

    private final ToolRegistry toolRegistry;
    private final PluginsProperties properties;
    private final List<Plugin> loadedPlugins = new ArrayList<>();

    public PluginLoader(ToolRegistry toolRegistry, PluginsProperties properties) {
        this.toolRegistry = toolRegistry;
        this.properties = properties;
    }

    /**
     * Load a plugin: register all its actions in the tool registry.
     */
    public void load(Plugin plugin) {
        if (properties.disabled().contains(plugin.name())) {
            log.info("Plugin '{}' is disabled, skipping.", plugin.name());
            return;
        }

        log.info("Loading plugin: {} — {}", plugin.name(), plugin.description());

        plugin.onLoad();
        toolRegistry.registerAll(plugin.actions());
        loadedPlugins.add(plugin);

        log.info("Plugin '{}' loaded with {} action(s).", plugin.name(), plugin.actions().size());
    }

    /**
     * Load all plugins provided as Spring beans.
     */
    public void loadAll(List<Plugin> plugins) {
        for (Plugin plugin : plugins) {
            try {
                load(plugin);
            } catch (Exception e) {
                log.error("Failed to load plugin '{}': {}", plugin.name(), e.getMessage(), e);
            }
        }
        log.info("Loaded {} plugin(s) total.", loadedPlugins.size());
    }

    public List<Plugin> loadedPlugins() {
        return List.copyOf(loadedPlugins);
    }
}
