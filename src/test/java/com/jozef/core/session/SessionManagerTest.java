package com.jozef.core.session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SessionManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void restoresConversationAcrossInstances() {
        Path file = tempDir.resolve("session.json");

        SessionManager first = new SessionManager(file.toString());
        first.current().addTurn("user", "cześć");
        first.current().addTurn("assistant", "Dzień dobry!");
        first.persist();

        SessionManager restored = new SessionManager(file.toString());
        assertThat(restored.current().id()).isEqualTo(first.current().id());
        assertThat(restored.current().conversation()).hasSize(2);
        assertThat(restored.current().conversation().get(0).role()).isEqualTo("user");
        assertThat(restored.current().conversation().get(1).content()).isEqualTo("Dzień dobry!");
    }

    @Test
    void createsFreshSessionWhenFileMissing() {
        SessionManager manager = new SessionManager(tempDir.resolve("nope.json").toString());
        assertThat(manager.current().id()).isNotBlank();
        assertThat(manager.current().conversation()).isEmpty();
    }
}
