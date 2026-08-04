package com.jozef.common.id;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SystemIdGenerator implements IdGenerator {

    @Override
    public UUID generate() {
        return UUID.randomUUID();
    }
}
