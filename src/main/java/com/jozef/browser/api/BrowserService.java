package com.jozef.browser.api;

/**
 * Browser automation, hidden behind an interface so the core never depends
 * on Playwright (or any specific browser engine).
 *
 * The LLM does NOT generate Playwright code. It calls browser tools, which
 * call this service, which talks to Playwright.
 */
public interface BrowserService {

    void open();

    void close();

    boolean isOpen();

    void navigate(String url);

    String currentUrl();

    String getPageTitle();

    String getPageText();

    void click(String selector);

    void type(String selector, String text);

    String readText(String selector);

    String screenshot();

    void search(String query);

    /** Runs a JavaScript expression in the page and returns its string result. */
    String evaluate(String expression);
}
