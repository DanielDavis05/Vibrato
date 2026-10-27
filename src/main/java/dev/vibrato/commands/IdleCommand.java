package dev.vibrato.commands;

import dev.vibrato.Vibrato;
import dev.vibrato.commandsystem.ArgParser;
import dev.vibrato.commandsystem.Command;
import dev.vibrato.tasks.movement.IdleTask;

public class IdleCommand extends Command {
    public IdleCommand() {
        super("idle", "Stand still");
    }

    @Override
    protected void call(Vibrato mod, ArgParser parser) {
        mod.runUserTask(new IdleTask(), this::finish);
    }
}
