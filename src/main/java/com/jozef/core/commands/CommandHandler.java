package com.jozef.core.commands;

/**
 * Handles a specific command type. Exactly one handler per command type.
 */
public interface CommandHandler<C extends Command> {
    void handle(C command);
}
