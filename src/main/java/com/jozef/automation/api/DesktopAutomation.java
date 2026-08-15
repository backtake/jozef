package com.jozef.automation.api;

import java.util.List;

/**
 * OS-level desktop automation: mouse, keyboard, windows, clipboard, screenshots.
 *
 * This is the platform abstraction. Core code depends only on this interface;
 * Windows (or later Linux/macOS) implementations live behind it.
 */
public interface DesktopAutomation {

    String platform();

    void launchApplication(String nameOrPath);

    void closeApplication(String nameOrProcess);

    void focusWindow(String titleOrProcess);

    List<String> listWindows();

    String getActiveWindowTitle();

    void click(int x, int y);

    void doubleClick(int x, int y);

    void moveMouse(int x, int y);

    void scroll(int amount);

    void typeText(String text);

    void pressKey(String keyCombo);

    void hotkey(String... keys);

    String readClipboard();

    void writeClipboard(String text);

    String takeScreenshot();
}
