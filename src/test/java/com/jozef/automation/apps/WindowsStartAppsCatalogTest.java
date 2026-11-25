package com.jozef.automation.apps;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WindowsStartAppsCatalogTest {

    @Test
    void parsesArrayOutput() throws Exception {
        var apps = new WindowsStartAppsCatalog().parse(
                "﻿[{\"Name\":\"Spotify\",\"AppID\":\"Spotify.App\"},{\"Name\":\"Eksplorator plików\",\"AppID\":\"Microsoft.Windows.Explorer\"},{\"Name\":\"\",\"AppID\":\"x\"}]");
        assertThat(apps).containsExactly(
                new InstalledApp("Spotify", "Spotify.App"),
                new InstalledApp("Eksplorator plików", "Microsoft.Windows.Explorer"));
    }

    @Test
    void parsesSingleObjectAndEmptyOutput() throws Exception {
        var catalog = new WindowsStartAppsCatalog();
        assertThat(catalog.parse("{\"Name\":\"Brave\",\"AppID\":\"Brave\"}")).containsExactly(new InstalledApp("Brave", "Brave"));
        assertThat(catalog.parse("  ")).isEmpty();
    }
}
