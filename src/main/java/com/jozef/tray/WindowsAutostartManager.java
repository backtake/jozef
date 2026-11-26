package com.jozef.tray;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Windows implementation of {@link AutostartManager} using the HKCU Run key
 * via the {@code reg.exe} tool. No native dependencies required.
 */
@Slf4j
public class WindowsAutostartManager implements AutostartManager {

    private static final String RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";

    @Override
    public void enable(String appName, String command) {
        run("reg", "add", RUN_KEY, "/v", appName, "/t", "REG_SZ", "/d", command, "/f");
    }

    @Override
    public void disable(String appName) {
        run("reg", "delete", RUN_KEY, "/v", appName, "/f");
    }

    @Override
    public boolean isEnabled(String appName) {
        return runSucceeds("reg", "query", RUN_KEY, "/v", appName);
    }

    /**
     * Builds the startup command: {@code javaw} (no console window) with the working
     * directory pinned to the current one — Windows starts Run-key entries in
     * System32, where Jozef's relative {@code data/} folder would not be found.
     */
    static String currentJarCommand() {
        Path jar = findBootJar();
        if (jar == null) {
            return null;
        }
        Path javaw = Path.of(System.getProperty("java.home"), "bin", "javaw.exe");
        String javaBin = (Files.exists(javaw) ? javaw : Path.of(System.getProperty("java.home"), "bin", "java.exe")).toString();
        return buildCommand(javaBin, jar.toAbsolutePath().toString(), System.getProperty("user.dir"));
    }

    /** Pure command builder (package-private for tests). */
    static String buildCommand(String javaBin, String jarPath) {
        return "\"" + javaBin + "\" -jar \"" + jarPath + "\"";
    }

    static String buildCommand(String javaBin, String jarPath, String workingDir) {
        if (workingDir == null || workingDir.isBlank()) {
            return buildCommand(javaBin, jarPath);
        }
        // A trailing backslash would escape the closing quote.
        String dir = workingDir.replaceAll("[\\\\/]+$", "");
        return "\"" + javaBin + "\" \"-Duser.dir=" + dir + "\" -jar \"" + jarPath + "\"";
    }

    private static Path findBootJar() {
        Path libs = Path.of(System.getProperty("user.dir"), "build", "libs");
        if (!Files.isDirectory(libs)) {
            return null;
        }
        try (var stream = Files.list(libs)) {
            return stream.filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(".jar") && !name.endsWith("-plain.jar");
                    })
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private void run(String... args) {
        try {
            Process process = new ProcessBuilder(args).redirectErrorStream(true).start();
            int code = process.waitFor();
            log.info("Autostart command '{}' exited with {}", String.join(" ", args), code);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Autostart command failed: {}", e.getMessage());
        }
    }

    private boolean runSucceeds(String... args) {
        try {
            Process process = new ProcessBuilder(args).redirectErrorStream(true).start();
            return process.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }
}
