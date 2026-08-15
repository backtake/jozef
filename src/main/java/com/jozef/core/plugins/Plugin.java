package com.jozef.core.plugins;

import com.jozef.core.action.Action;

import java.util.List;

/**
 * A Plugin provides a set of tools (Actions).
 * This is the equivalent of an MCP server — it registers tools
 * that the LLM can call.
 *
 * Examples: BrowserPlugin, SpotifyPlugin, FileSystemPlugin, GitPlugin
 */
public interface Plugin {
    /** Unique plugin name, e.g. "browser", "spotify", "filesystem" */
    String name();

    /** Human-readable description for logging */
    String description();

    /** The actions (tools) this plugin provides */
    List<Action> actions();

    /** Called when the plugin is loaded */
    default void onLoad() {}

    /** Called when the plugin is unloaded */
    default void onUnload() {}
}
