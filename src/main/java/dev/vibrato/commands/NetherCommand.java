package dev.vibrato.commands;

import dev.vibrato.Vibrato;
import dev.vibrato.commandsystem.ArgParser;
import dev.vibrato.commandsystem.Command;
import dev.vibrato.tasks.movement.DefaultGoToDimensionTask;
import dev.vibrato.util.Dimension;

/** Uses the existing survival progression task to enter the Nether. */
public final class NetherCommand extends Command {
    public NetherCommand() { super("nether", "Enter the Nether using survival progression"); }
    @Override protected void call(Vibrato mod, ArgParser parser) {
        mod.runUserTask(new DefaultGoToDimensionTask(Dimension.NETHER), this::finish);
    }
}
