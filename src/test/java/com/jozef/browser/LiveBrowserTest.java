package com.jozef.browser;

import com.jozef.configuration.BrowserProperties;
import com.jozef.core.action.ActionResult;
import com.jozef.core.tool.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Real headless browser against the internet — run manually with JOZEF_LIVE=1. */
@EnabledIfEnvironmentVariable(named = "JOZEF_LIVE", matches = "1")
class LiveBrowserTest {

    @Test
    void looksUpAndReadsPages() {
        ToolRegistry tools = new ToolRegistry();
        BrowserCapability browser = new BrowserCapability(new BrowserProperties(true, true, "chrome", null, null), tools);
        browser.initialize();
        try {
            ActionResult lookup = tools.execute("web_lookup", Map.of("query", "pogoda Open-Meteo"));
            System.out.println("LOOKUP: " + lookup.message().substring(0, Math.min(400, lookup.message().length())));
            assertThat(lookup.success()).isTrue();
            assertThat(lookup.message()).contains("https://");

            ActionResult page = tools.execute("read_web_page", Map.of("url", "https://example.com"));
            System.out.println("PAGE: " + page.message());
            assertThat(page.message()).contains("Example Domain");
        } finally {
            browser.shutdown();
        }
    }
}
