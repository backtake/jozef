package com.jozef.application.goal;

import com.jozef.common.command.Command;

public record CreateGoalCommand(String name) implements Command {
}
