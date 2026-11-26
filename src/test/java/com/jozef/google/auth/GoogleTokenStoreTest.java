package com.jozef.google.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleTokenStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndLoadsTokens() {
        Path file = tempDir.resolve("tokens.json");
        GoogleTokenStore store = new GoogleTokenStore(file.toString());
        GoogleTokens tokens = new GoogleTokens(
                "access-123", "refresh-456", System.currentTimeMillis() + 3_600_000L, "scope1 scope2");

        store.save(tokens);
        GoogleTokens loaded = store.load();

        assertThat(loaded).isNotNull();
        assertThat(loaded.accessToken()).isEqualTo("access-123");
        assertThat(loaded.refreshToken()).isEqualTo("refresh-456");
        assertThat(loaded.scope()).isEqualTo("scope1 scope2");
    }

    @Test
    void unusableTokensAreForgottenSoSignInCanStartAgain() {
        Path file = tempDir.resolve("tokens.json");
        GoogleTokenStore store = new GoogleTokenStore(file.toString());
        // Expired access token and no refresh token: never wait for the user here.
        store.save(new GoogleTokens("old", null, System.currentTimeMillis() - 1_000L, null));
        GoogleAuthService auth = new GoogleAuthService("id", "secret", store);

        org.assertj.core.api.Assertions.assertThatThrownBy(auth::accessToken)
                .isInstanceOf(GoogleNotSignedInException.class);
        assertThat(file).doesNotExist();
        assertThat(auth.isSignedIn()).isFalse();
        assertThat(auth.verifySignedIn()).isFalse();
    }

    @Test
    void returnsNullWhenFileMissing() {
        GoogleTokenStore store = new GoogleTokenStore(tempDir.resolve("missing.json").toString());
        assertThat(store.load()).isNull();
    }

    @Test
    void detectsExpiry() {
        GoogleTokens expired = new GoogleTokens("a", "r", System.currentTimeMillis() - 1_000L, null);
        GoogleTokens valid = new GoogleTokens("a", "r", System.currentTimeMillis() + 3_600_000L, null);

        assertThat(expired.isExpired()).isTrue();
        assertThat(valid.isExpired()).isFalse();
    }

    @Test
    void fallsBackToDefaultPathWhenBlank() {
        GoogleTokenStore store = new GoogleTokenStore("   ");
        assertThat(store.path().getFileName().toString()).isEqualTo("google-tokens.json");
        assertThat(store.path().toString()).contains("data");
    }
}
