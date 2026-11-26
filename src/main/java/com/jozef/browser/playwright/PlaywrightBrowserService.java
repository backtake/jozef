package com.jozef.browser.playwright;

import com.jozef.browser.api.BrowserService;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import lombok.extern.slf4j.Slf4j;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Browser automation backed by Playwright.
 *
 * Uses the installed Edge/Chrome via {@code channel} by default, so no browser
 * binaries need to be downloaded. Supports a persistent profile for
 * already-authenticated sessions (e.g. Gmail).
 */
@Slf4j
public class PlaywrightBrowserService implements BrowserService {

    private final boolean headless;
    private final String channel;
    private final String profileDir;
    private final String executablePath;

    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;

    public PlaywrightBrowserService(boolean headless, String channel, String profileDir, String executablePath) {
        this.headless = headless;
        this.channel = channel;
        this.profileDir = profileDir;
        this.executablePath = executablePath;
    }

    @Override
    public synchronized void open() {
        if (page != null) {
            return;
        }
        log.info("Launching browser: channel={}, headless={}", channel, headless);

        playwright = Playwright.create();
        BrowserType chromium = playwright.chromium();

        if (profileDir != null && !profileDir.isBlank()) {
            BrowserType.LaunchPersistentContextOptions options =
                    new BrowserType.LaunchPersistentContextOptions().setHeadless(headless);
            if (channel != null && !channel.isBlank()) {
                options.setChannel(channel);
            }
            if (executablePath != null && !executablePath.isBlank()) {
                options.setExecutablePath(Path.of(executablePath));
            }
            context = chromium.launchPersistentContext(Path.of(profileDir), options);
            page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
        } else {
            BrowserType.LaunchOptions options = new BrowserType.LaunchOptions().setHeadless(headless);
            if (channel != null && !channel.isBlank()) {
                options.setChannel(channel);
            }
            if (executablePath != null && !executablePath.isBlank()) {
                options.setExecutablePath(Path.of(executablePath));
            }
            browser = chromium.launch(options);
            context = browser.newContext();
            page = context.newPage();
        }

        log.info("Browser launched.");
    }

    @Override
    public synchronized void close() {
        if (context != null) {
            try { context.close(); } catch (Exception ignored) {}
        }
        if (browser != null) {
            try { browser.close(); } catch (Exception ignored) {}
        }
        if (playwright != null) {
            try { playwright.close(); } catch (Exception ignored) {}
        }
        page = null;
        context = null;
        browser = null;
        playwright = null;
        log.info("Browser closed.");
    }

    @Override
    public boolean isOpen() {
        return page != null;
    }

    @Override
    public void navigate(String url) {
        openIfNeeded();
        log.info("Navigating to {}", url);
        page.navigate(url);
        // Many pages (search results, news) render their content with JavaScript after "load".
        try {
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(8_000));
        } catch (com.microsoft.playwright.TimeoutError ignored) {
            // busy pages never go idle — read whatever has rendered
        }
    }

    @Override
    public String currentUrl() {
        openIfNeeded();
        return page.url();
    }

    @Override
    public String getPageTitle() {
        openIfNeeded();
        return page.title();
    }

    @Override
    public String getPageText() {
        openIfNeeded();
        return page.innerText("body");
    }

    @Override
    public void click(String selector) {
        openIfNeeded();
        page.click(selector);
    }

    @Override
    public void type(String selector, String text) {
        openIfNeeded();
        page.fill(selector, text);
    }

    @Override
    public String readText(String selector) {
        openIfNeeded();
        return page.innerText(selector);
    }

    @Override
    public String screenshot() {
        openIfNeeded();
        String filename = "browser_" + Instant.now().toString().replace(":", "-") + ".png";
        page.screenshot(new Page.ScreenshotOptions().setPath(Path.of(filename)));
        return filename;
    }

    @Override
    public void search(String query) {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        navigate("https://www.google.com/search?q=" + encoded);
    }

    private void openIfNeeded() {
        if (page == null) {
            open();
        }
    }
}
