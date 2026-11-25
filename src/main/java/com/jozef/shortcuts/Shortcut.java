package com.jozef.shortcuts;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * A user-defined shortcut: a name (plus spoken aliases) that opens either a
 * website ({@code url}) or an application ({@code app} — a Start-menu name,
 * an .exe path or a URI such as {@code spotify:}).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record Shortcut(String name, List<String> aliases, String url, String app) {

    public Shortcut {
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
    }

    public static Shortcut website(String name, String url, String... aliases) {
        return new Shortcut(name, List.of(aliases), url, null);
    }

    public static Shortcut application(String name, String app, String... aliases) {
        return new Shortcut(name, List.of(aliases), null, app);
    }

    @JsonIgnore
    public boolean isWebsite() {
        return url != null && !url.isBlank();
    }

    @JsonIgnore
    public String target() {
        return isWebsite() ? url : app;
    }
}
