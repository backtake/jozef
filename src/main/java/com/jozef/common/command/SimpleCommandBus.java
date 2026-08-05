package com.jozef.common.command;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleCommandBus implements CommandBus {

    private final Map<Class<?>, CommandHandler<?>> handlers = new ConcurrentHashMap<>();

    public <C extends Command> void register(Class<C> commandType, CommandHandler<C> handler) {
        handlers.put(commandType, handler);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <C extends Command> void dispatch(C command) {
        CommandHandler<C> handler = (CommandHandler<C>) handlers.get(command.getClass());
        if (handler == null) {
            throw new IllegalArgumentException("No handler registered for " + command.getClass().getSimpleName());
        }
        handler.handle(command);
    }
}
