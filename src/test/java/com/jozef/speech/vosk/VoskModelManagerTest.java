package com.jozef.speech.vosk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VoskModelManagerTest {

    @Test
    void derivesModelNameAndDownloadUrl() {
        assertThat(VoskModelManager.modelName("data/vosk-model-small-pl-0.22"))
                .isEqualTo("vosk-model-small-pl-0.22");
        assertThat(VoskModelManager.modelName("vosk-model-pl-0.22"))
                .isEqualTo("vosk-model-pl-0.22");
        assertThat(VoskModelManager.downloadUrl("data/vosk-model-small-pl-0.22"))
                .isEqualTo("https://alphacephei.com/vosk/models/vosk-model-small-pl-0.22.zip");
    }

    @Test
    void extractsZipWithTopLevelDirectory(@TempDir Path tempDir) throws IOException {
        Path zip = tempDir.resolve("model.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("model/"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("model/am/info.txt"));
            zos.write("hello".getBytes());
            zos.closeEntry();
        }

        Path dest = tempDir.resolve("data");
        VoskModelManager.extract(zip, dest);

        assertThat(Files.exists(dest.resolve("model/am/info.txt"))).isTrue();
        assertThat(Files.readString(dest.resolve("model/am/info.txt"))).isEqualTo("hello");
    }

    @Test
    void rejectsZipEntryEscapingDestination(@TempDir Path tempDir) throws IOException {
        Path zip = tempDir.resolve("evil.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("../evil.txt"));
            zos.write("x".getBytes());
            zos.closeEntry();
        }

        Path dest = tempDir.resolve("data");
        assertThatThrownBy(() -> VoskModelManager.extract(zip, dest))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Niebezpieczny");
    }
}
