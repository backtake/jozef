package com.jozef.common.command;

public interface CommandBus {
    <C extends Command> void dispatch(C command);
}
