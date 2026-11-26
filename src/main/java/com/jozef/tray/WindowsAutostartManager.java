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

    /**
     * Writes the Run value through PowerShell with the command Base64-encoded: passing
     * a value that contains quotes to reg.exe via ProcessBuilder splits it into
     * several arguments and the write fails. The value is read back to verify.
     */
    @Override
    public void enable(String appName, String command) {
        String b64 = java.util.Base64.getEncoder().encodeToString(command.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String name = appName.replace("'", "''");
        String script = "$v = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('" + b64 + "')); "
                + "Set-ItemProperty -Path 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Run' -Name '" + name
                + "' -Value $v; "
                + "if ((Get-ItemProperty -Path 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Run' -Name '" + name
                + "').'" + name + "' -ne $v) { exit 3 }";
        boolean ok = runSucceeds("powershell", "-NoProfile", "-NonInteractive", "-Command", script);
        if (ok) {
            log.info("Autostart enabled: {}", command);
        } else {
            log.warn("Could not enable autostart (writing HKCU Run failed).");
        }
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
     * Builds the startup command. Windows starts Run-key entries in System32, so the
     * real working directory is set with {@code start /D} (a {@code -Duser.dir}
     * property is not enough: java.io, native code such as Vosk and child processes
     * still use the process directory). {@code javaw} keeps it windowless.
     */
    static String currentJarCommand() {
        Path jar = findBootJar();
        if (jar == null) {
            return null;
        }
        Path javaw = Path.of(System.getProperty("java.home"), "bin", "javaw.exe");
        String javaBin = (Files.exists(javaw) ? javaw : Path.of(System.getProperty("java.home"), "bin", "java.exe")).toString();
        return buildCommand(javaBin, jar.toAbsolutePath().toString(), Path.of("").toAbsolutePath().toString());
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
        String cmd = Path.of(System.getenv().getOrDefault("SystemRoot", "C:\\Windows"), "System32", "cmd.exe").toString();
        return "\"" + cmd + "\" /c start \"Jozef\" /D \"" + dir + "\" \"" + javaBin + "\" -Xmx512m -jar \"" + jarPath + "\"";
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
