package com.jozef.automation.apps;

import java.util.List;

/** Source of the applications installed on this machine. Platform-specific. */
public interface InstalledAppsCatalog {

    /** Cached list of installed apps (may trigger the first scan). */
    List<InstalledApp> apps();

    /** Re-scan the machine (e.g. after the user installed something). */
    void refresh();

    /** A catalog that knows nothing — for headless/tests/non-Windows. */
    static InstalledAppsCatalog empty() {
        return new InstalledAppsCatalog() {
            @Override public List<InstalledApp> apps() { return List.of(); }
            @Override public void refresh() {}
        };
    }

    static InstalledAppsCatalog of(List<InstalledApp> apps) {
        List<InstalledApp> copy = List.copyOf(apps);
        return new InstalledAppsCatalog() {
            @Override public List<InstalledApp> apps() { return copy; }
            @Override public void refresh() {}
        };
    }
}
