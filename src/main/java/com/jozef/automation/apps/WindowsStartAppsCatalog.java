package com.jozef.automation.apps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Installed apps from the Windows Start menu ({@code Get-StartApps}): covers
 * classic desktop programs, Store/UWP apps and shortcuts alike. Scanned lazily
 * and cached; {@link #refresh()} re-scans.
 */
@Slf4j
public class WindowsStartAppsCatalog implements InstalledAppsCatalog {

    private static final String SCRIPT =
            "[Console]::OutputEncoding = [Text.Encoding]::UTF8; "
                    + "Get-StartApps | Select-Object Name, AppID | ConvertTo-Json -Compress";

    private final ObjectMapper mapper = new ObjectMapper();
    private volatile List<InstalledApp> cache;

    @Override
    public List<InstalledApp> apps() {
        List<InstalledApp> current = cache;
        if (current == null) {
            synchronized (this) {
                if (cache == null) {
                    cache = scan();
                }
                current = cache;
            }
        }
        return current;
    }

    @Override
    public synchronized void refresh() {
        cache = scan();
    }

    private List<InstalledApp> scan() {
        try {
            Process process = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", SCRIPT)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            byte[] out = process.getInputStream().readAllBytes();
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                log.warn("Get-StartApps timed out");
                return List.of();
            }
            List<InstalledApp> apps = parse(new String(out, StandardCharsets.UTF_8));
            log.info("Found {} installed applications in the Start menu", apps.size());
            return apps;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (Exception e) {
            log.warn("Cannot list installed applications: {}", e.getMessage());
            return List.of();
        }
    }

    List<InstalledApp> parse(String json) throws Exception {
        String trimmed = json == null ? "" : json.trim();
        if (trimmed.startsWith("﻿")) trimmed = trimmed.substring(1);
        if (trimmed.isEmpty()) return List.of();
        JsonNode root = mapper.readTree(trimmed);
        List<InstalledApp> apps = new ArrayList<>();
        if (root.isArray()) {
            root.forEach(node -> add(apps, node));
        } else {
            add(apps, root); // a single app is serialised as an object
        }
        return List.copyOf(apps);
    }

    private static void add(List<InstalledApp> apps, JsonNode node) {
        String name = node.path("Name").asText("");
        String id = node.path("AppID").asText("");
        if (!name.isBlank() && !id.isBlank()) {
            apps.add(new InstalledApp(name, id));
        }
    }
}
