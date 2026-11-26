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

        browser = new PlaywrightBrowserService(props.headless(), props.channel(), props.profileDir(), props.executablePath());
        registerReadingTools();
        if (Boolean.getBoolean("jozef.browser.advanced-tools")
                || "true".equalsIgnoreCase(System.getenv("JOZEF_BROWSER_ADVANCED_TOOLS"))) {
            registerTools();
        }
        log.info("Browser capability initialized ({} tools registered).", toolRegistry.size());
    }

    /**
     * Two high-level tools instead of nine low-level ones: a model that cannot see the
     * DOM cannot use CSS selectors well, and every extra tool makes small models choose
     * worse. Opening a site FOR the user is open_url (their own browser and logins).
     */
    private void registerReadingTools() {
        toolRegistry.register(action("read_web_page",
                "Read the text of a web page (in the background) to answer a question about it: an article, "
                        + "a recipe, opening hours. Not for showing a page to the user — use open_url for that.",
                new ToolParameters().string("url", "Full http(s) address", true).build(),
                ctx -> {
                    String url = ctx.getString("url");
                    if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
                        return ActionResult.fail("Only http(s) addresses can be read");
                    }
                    if (!isPublicAddress(url)) {
                        // A page/e-mail could tell the model to read the router panel or a local
                        // service and leak it; only public internet pages are readable.
                        return ActionResult.fail("Only public internet pages can be read (not local or private network addresses)");
                    }
                    browser.navigate(url);
                    return ActionResult.ok(browser.getPageTitle() + "\n" + limit(browser.getPageText(), 8000));
                }).withTimeoutSeconds(60));

        toolRegistry.register(action("web_lookup",
                "Search the web in the background and return the result titles, links and snippets, for questions "
                        + "that need current information (news, prices, opening hours, facts you are unsure about). "
                        + "Follow up with read_web_page on the best link when needed.",
                new ToolParameters().string("query", "Search query", true).build(),
                ctx -> {
                    String q = java.net.URLEncoder.encode(ctx.getString("query"), java.nio.charset.StandardCharsets.UTF_8);
                    browser.navigate("https://www.bing.com/search?setlang=pl&q=" + q);
                    String results = browser.evaluate(BING_RESULTS_JS);
                    if (!results.isBlank()) {
                        return ActionResult.ok(limit(results, 6000));
                    }
                    String text = browser.getPageText();
                    String lower = text.toLowerCase();
                    if (lower.contains("captcha") || lower.contains("nie jesteś robotem") || lower.contains("verify you are human")) {
                        return ActionResult.fail("The search engine asked for a CAPTCHA — offer to open the search in the user's browser (search_web)");
                    }
                    return ActionResult.ok(limit(text, 6000));
                }).withTimeoutSeconds(60));
    }

    /** Extracts "title | link | snippet" lines from a Bing results page (empty when none). */
    static final String BING_RESULTS_JS = """
            () => Array.from(document.querySelectorAll('li.b_algo')).slice(0, 8).map(li => {
              const a = li.querySelector('h2 a');
              const p = li.querySelector('.b_caption p, p');
              let href = a ? a.href : '';
              try {
                // Bing wraps links: /ck/a?...&u=a1<base64url of the real address>
                const u = new URL(href).searchParams.get('u');
                if (u && u.startsWith('a1')) {
                  const b64 = u.slice(2).replace(/-/g, '+').replace(/_/g, '/');
                  href = atob(b64 + '='.repeat((4 - b64.length % 4) % 4));
                }
              } catch (e) { }
              return (a ? a.innerText.trim() : '') + ' | ' + href + ' | ' + (p ? p.innerText.trim() : '');
            }).join('\\n')
            """;

    /** True when every address the host resolves to is a public internet address. */
    static boolean isPublicAddress(String url) {
        try {
            String host = java.net.URI.create(url).getHost();
            if (host == null || host.isBlank()) return false;
            String h = host.toLowerCase();
            if (h.equals("localhost") || h.endsWith(".local") || h.endsWith(".localhost") || h.endsWith(".internal")
                    || h.endsWith(".lan") || h.endsWith(".home")) {
                return false;
            }
            java.net.InetAddress[] addresses = java.net.InetAddress.getAllByName(host);
            for (java.net.InetAddress a : addresses) {
                if (a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress() || a.isAnyLocalAddress()
                        || a.isMulticastAddress() || isUniqueLocalV6(a) || isCarrierGradeNat(a)) {
                    return false;
                }
            }
            return addresses.length > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isUniqueLocalV6(java.net.InetAddress a) {
        byte[] b = a.getAddress();
        return b.length == 16 && (b[0] & 0xFE) == 0xFC; // fc00::/7
    }

    private static boolean isCarrierGradeNat(java.net.InetAddress a) {
        byte[] b = a.getAddress();
        return b.length == 4 && (b[0] & 0xFF) == 100 && (b[1] & 0xC0) == 64; // 100.64.0.0/10
    }

    private static String limit(String text, int max) {
        if (text == null) return "";
        String t = text.replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
        return t.length() > max ? t.substring(0, max) + "…" : t;
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

    private LambdaAction action(String name, String description, java.util.Map<String, Object> schema,
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
