package com.jozef.automation.apps;

/**
 * An application installed on the machine, as listed in the Start menu.
 *
 * @param name  display name ("Spotify", "Eksplorator plików")
 * @param appId Windows AppUserModelID / Start menu id — launchable via {@code shell:AppsFolder\<appId>}
 */
public record InstalledApp(String name, String appId) {}
