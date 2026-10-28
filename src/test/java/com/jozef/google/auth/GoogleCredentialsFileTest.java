package com.jozef.google.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleCredentialsFileTest {

    @TempDir
    Path tempDir;

    private Path write(String content) throws Exception {
        Path file = tempDir.resolve("credentials.json");
        Files.writeString(file, content);
        return file;
    }

    @Test
    void loadsInstalledShape() throws Exception {
        Path file = write("{\"installed\":{\"client_id\":\"id-1\",\"client_secret\":\"secret-1\",\"redirect_uris\":[\"http://localhost\"]}}");

        GoogleCredentialsFile creds = GoogleCredentialsFile.load(file.toString());

        assertThat(creds).isNotNull();
        assertThat(creds.clientId()).isEqualTo("id-1");
        assertThat(creds.clientSecret()).isEqualTo("secret-1");
    }

    @Test
    void loadsWebShape() throws Exception {
        Path file = write("{\"web\":{\"client_id\":\"id-2\",\"client_secret\":\"secret-2\"}}");

        GoogleCredentialsFile creds = GoogleCredentialsFile.load(file.toString());

        assertThat(creds).isNotNull();
        assertThat(creds.clientId()).isEqualTo("id-2");
        assertThat(creds.clientSecret()).isEqualTo("secret-2");
    }

    @Test
    void loadsFlatShape() throws Exception {
        Path file = write("{\"client_id\":\"id-3\",\"client_secret\":\"secret-3\"}");

        GoogleCredentialsFile creds = GoogleCredentialsFile.load(file.toString());

        assertThat(creds).isNotNull();
        assertThat(creds.clientId()).isEqualTo("id-3");
        assertThat(creds.clientSecret()).isEqualTo("secret-3");
    }

    @Test
    void returnsNullWhenMissing() {
        assertThat(GoogleCredentialsFile.load(tempDir.resolve("nope.json").toString())).isNull();
        assertThat(GoogleCredentialsFile.load(null)).isNull();
        assertThat(GoogleCredentialsFile.load("")).isNull();
    }

    @Test
    void returnsNullWhenInvalid() throws Exception {
        Path file = write("{\"installed\":{\"client_id\":\"id\"}}"); // no secret
        assertThat(GoogleCredentialsFile.load(file.toString())).isNull();

        Path garbage = write("not json");
        assertThat(GoogleCredentialsFile.load(garbage.toString())).isNull();
    }
}
