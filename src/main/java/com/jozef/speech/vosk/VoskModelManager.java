package com.jozef.speech.vosk;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads and installs a Vosk model automatically, so a non-technical user
 * never has to. The model zip is fetched from alphacephei.com and extracted into
 * the configured directory (default {@code data/vosk-model-small-pl-0.22}).
 */
@Slf4j
public final class VoskModelManager {

    private static final String BASE_URL = "https://alphacephei.com/vosk/models/";
    private static final long LOG_EVERY_BYTES = 5L * 1024 * 1024; // log progress every 5 MB

    private VoskModelManager() {}

    public static boolean isInstalled(String modelPath) {
        return VoskSpeechRecognizer.isModelAvailable(modelPath);
    }

    /** Downloads and extracts the model if missing. Returns true when ready to use. */
    public static boolean install(String modelPath) {
        if (isInstalled(modelPath)) {
            return true;
        }

        Path targetDir = Path.of(modelPath).toAbsolutePath();
        Path parent = targetDir.getParent();
        if (parent == null) {
            parent = Path.of("data").toAbsolutePath();
        }
        String modelName = modelName(modelPath);
        Path zip = parent.resolve(modelName + ".zip");

        try {
            Files.createDirectories(parent);
            log.info("Model Vosk '{}' nie znaleziony — pobieram z {}", modelName, downloadUrl(modelPath));
            if (!download(downloadUrl(modelPath), zip)) {
                log.warn("Nie udało się pobrać modelu Vosk.");
                return false;
            }
            log.info("Rozpakowywanie modelu do {}", parent);
            extract(zip, parent);
            Files.deleteIfExists(zip);

            boolean ok = isInstalled(targetDir.toString());
            if (ok) {
                log.info("Model Vosk zainstalowany w {}", targetDir);
            } else {
                log.warn("Model pobrany, ale nie znaleziono katalogu {}", targetDir);
            }
            return ok;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Instalacja modelu Vosk nie powiodła się: {}", e.getMessage());
            try {
                Files.deleteIfExists(zip);
            } catch (IOException ignored) {
                // best effort cleanup
            }
            return false;
        }
    }

    private static boolean download(String url, Path target) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(10))
                .GET()
                .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            return false;
        }

        long total = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        long downloaded = 0;
        long nextLog = LOG_EVERY_BYTES;

        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[65536];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
                downloaded += n;
                if (downloaded >= nextLog) {
                    logProgress(downloaded, total);
                    nextLog += LOG_EVERY_BYTES;
                }
            }
        }
        logProgress(downloaded, total);
        return true;
    }

    private static void logProgress(long downloaded, long total) {
        String totalText = total > 0 ? " / " + (total / (1024 * 1024)) + " MB" : "";
        log.info("  ... pobrano {} MB{}", downloaded / (1024 * 1024), totalText);
    }

    /** Extracts a zip into {@code destDir}, rejecting entries that escape it. */
    static void extract(Path zipPath, Path destDir) throws IOException {
        Path dest = destDir.normalize();
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipPath)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path out = dest.resolve(entry.getName()).normalize();
                if (!out.startsWith(dest)) {
                    throw new IOException("Niebezpieczny wpis w archiwum: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    try (OutputStream os = Files.newOutputStream(out)) {
                        zis.transferTo(os);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    /** Last path segment, e.g. {@code data/vosk-model-small-pl-0.22} → {@code vosk-model-small-pl-0.22}. */
    static String modelName(String modelPath) {
        String path = modelPath.replace('\\', '/');
        int idx = path.lastIndexOf('/');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }

    static String downloadUrl(String modelPath) {
        return BASE_URL + modelName(modelPath) + ".zip";
    }
}
