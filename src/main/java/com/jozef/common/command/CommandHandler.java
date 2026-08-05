package com.jozef.common.command;

public interface CommandHandler<C extends Command> {
    void handle(C command);
}
