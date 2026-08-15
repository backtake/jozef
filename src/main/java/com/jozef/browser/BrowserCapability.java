package com.jozef.browser;

import com.jozef.browser.api.BrowserService;
import com.jozef.browser.playwright.PlaywrightBrowserService;
import com.jozef.configuration.BrowserProperties;
import com.jozef.core.action.Action;
import com.jozef.core.action.ActionContext;
import com.jozef.core.action.ActionResult;
import com.jozef.core.action.ExecutionStrategy;
import com.jozef.core.action.LambdaAction;
import com.jozef.core.capabilities.Capability;
import com.jozef.core.tool.ToolParameters;
import com.jozef.core.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

/**
 * Browser capability: launches Playwright and registers browser tools.
 */
@Slf4j
public class BrowserCapability implements Capability {

    private final BrowserProperties props;
    private final ToolRegistry toolRegistry;
    private BrowserService browser;

    public BrowserCapability(BrowserProperties props, ToolRegistry toolRegistry) {
        this.props = props;
        this.toolRegistry = toolRegistry;
    }

    public BrowserService browser() {
        return browser;
    }

    @Override
    public String name() {
        return "browser";
    }

    @Override
    public int priority() {
        return 40;
    }

    @Override
    public void initialize() {
        if (!props.enabled()) {
            log.info("Browser capability disabled by configuration.");
            return;
        }

        browser = new PlaywrightBrowserService(props.headless(), props.channel(), props.profileDir());
        registerTools();
        log.info("Browser capability initialized ({} tools registered).", toolRegistry.size());
    }

    private void registerTools() {
        toolRegistry.register(action("browser_navigate",
                "Navigate the browser to a given URL.",
                new ToolParameters().string("url", "Full URL to open", true).build(),
                ctx -> { browser.navigate(ctx.getString("url")); return ActionResult.ok("Navigated to " + ctx.getString("url")); }));

        toolRegistry.register(action("browser_search",
                "Search the web using Google in the browser.",
                new ToolParameters().string("query", "Search query", true).build(),
                ctx -> { browser.search(ctx.getString("query")); return ActionResult.ok("Searched for " + ctx.getString("query")); }));

        toolRegistry.register(action("browser_click",
                "Click an element on the page using a CSS selector.",
                new ToolParameters().string("selector", "CSS selector of the element", true).build(),
                ctx -> { browser.click(ctx.getString("selector")); return ActionResult.ok("Clicked " + ctx.getString("selector")); }));

        toolRegistry.register(action("browser_type",
                "Type text into an input field on the page.",
                new ToolParameters().string("selector", "CSS selector of the input", true)
                        .string("text", "Text to type", true).build(),
                ctx -> { browser.type(ctx.getString("selector"), ctx.getString("text")); return ActionResult.ok("Typed into " + ctx.getString("selector")); }));

        toolRegistry.register(action("browser_read_text",
                "Read the text content of an element on the page.",
                new ToolParameters().string("selector", "CSS selector of the element", true).build(),
                ctx -> { String text = browser.readText(ctx.getString("selector")); return ActionResult.ok(text, text); }));

        toolRegistry.register(action("browser_get_page_text",
                "Read the visible text of the whole page.",
                null,
                ctx -> { String text = browser.getPageText(); return ActionResult.ok(truncate(text), text); }));

        toolRegistry.register(action("browser_get_title",
                "Get the title of the current page.",
                null,
                ctx -> { String title = browser.getPageTitle(); return ActionResult.ok(title, title); }));

        toolRegistry.register(action("browser_get_url",
                "Get the URL of the current page.",
                null,
                ctx -> { String url = browser.currentUrl(); return ActionResult.ok(url, url); }));

        toolRegistry.register(action("browser_screenshot",
                "Take a screenshot of the current page.",
                null,
                ctx -> { String path = browser.screenshot(); return ActionResult.ok("Screenshot saved to " + path, path); }));
    }

    private Action action(String name, String description, java.util.Map<String, Object> schema,
                          java.util.function.Function<ActionContext, ActionResult> executor) {
        return new LambdaAction(name, description, schema, Set.of("BROWSER"), ExecutionStrategy.SAFE, executor);
    }

    private String truncate(String text) {
        if (text == null) return "";
        return text.length() > 2000 ? text.substring(0, 2000) : text;
    }

    @Override
    public void shutdown() {
        if (browser != null) {
            browser.close();
        }
        log.info("Browser capability shut down");
    }
}
