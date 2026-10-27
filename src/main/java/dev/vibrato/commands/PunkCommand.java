package dev.vibrato.commands;

import dev.vibrato.Vibrato;
import dev.vibrato.commandsystem.Arg;
import dev.vibrato.commandsystem.ArgParser;
import dev.vibrato.commandsystem.Command;
import dev.vibrato.commandsystem.CommandException;
import dev.vibrato.tasks.entity.KillPlayerTask;

public class PunkCommand extends Command {
    public PunkCommand() throws CommandException {
        super("punk", "Punk 'em", new Arg(String.class, "playerName"));
    }

    @Override
    protected void call(Vibrato mod, ArgParser parser) throws CommandException {
        String playerName = parser.get(String.class);
        mod.runUserTask(new KillPlayerTask(playerName), this::finish);
    }
}