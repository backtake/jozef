package com.jozef.core.commands;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class CommandBusImpl implements CommandBus {

    private final Map<Class<?>, CommandHandler<?>> handlers = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <C extends Command> void register(Class<C> commandType, CommandHandler<C> handler) {
        handlers.put(commandType, handler);
        log.debug("Registered handler {} for command {}", handler.getClass().getSimpleName(), commandType.getSimpleName());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <C extends Command> void dispatch(C command) {
        CommandHandler<C> handler = (CommandHandler<C>) handlers.get(command.getClass());
        if (handler == null) {
            throw new IllegalStateException("No handler registered for " + command.getClass().getSimpleName());
        }
        log.debug("Dispatching command {} to handler {}", command.getClass().getSimpleName(), handler.getClass().getSimpleName());
        handler.handle(command);
    }
}
