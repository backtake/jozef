package com.jozef.configuration;

import java.util.List;

public record PluginsProperties(
        boolean autoDiscover,
        List<String> directories,
        List<String> disabled
) {}
