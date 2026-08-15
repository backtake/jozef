package com.jozef.core.commands;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommandBusImplTest {

    record PingCommand(String value) implements Command {}
    record PongCommand() implements Command {}

    @Test
    void dispatchesToRegisteredHandler() {
        CommandBusImpl bus = new CommandBusImpl();
        AtomicInteger calls = new AtomicInteger();
        bus.register(PingCommand.class, c -> calls.incrementAndGet());

        bus.dispatch(new PingCommand("a"));

        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void throwsWhenNoHandlerRegistered() {
        CommandBusImpl bus = new CommandBusImpl();

        assertThatThrownBy(() -> bus.dispatch(new PongCommand()))
                .isInstanceOf(IllegalStateException.class);
    }
}
