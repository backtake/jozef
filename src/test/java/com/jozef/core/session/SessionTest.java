package com.jozef.core.session;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionTest {

    @Test
    void storesConversationTurns() {
        Session session = new Session();

        session.addTurn("user", "hello");
        session.addTurn("assistant", "hi");

        assertThat(session.conversation()).hasSize(2);
        assertThat(session.conversation().get(0).role()).isEqualTo("user");
        assertThat(session.conversation().get(1).content()).isEqualTo("hi");
    }

    @Test
    void storesAndTrimsActionHistory() {
        Session session = new Session();

        for (int i = 0; i < 25; i++) {
            session.addAction("action-" + i);
        }

        assertThat(session.lastActions()).hasSize(20);
        assertThat(session.lastActions().get(0)).isEqualTo("action-5");
    }

    @Test
    void resetCreatesFreshSession() {
        SessionManager manager = new SessionManager();
        String firstId = manager.current().id();

        Session fresh = manager.reset();

        assertThat(fresh.id()).isNotEqualTo(firstId);
    }
}
