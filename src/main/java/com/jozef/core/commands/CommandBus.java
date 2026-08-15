package com.jozef.core.commands;

public interface CommandBus {

    /**
     * Register a handler for a specific command type.
     * Exactly one handler per command type.
     */
    <C extends Command> void register(Class<C> commandType, CommandHandler<C> handler);

    /**
     * Dispatch a command to its registered handler.
     * Commands are intentional operations ("DO SOMETHING"), unlike events.
     */
    <C extends Command> void dispatch(C command);
}
