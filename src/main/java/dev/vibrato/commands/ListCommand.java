package dev.vibrato.commands;

import dev.vibrato.Vibrato;
import dev.vibrato.TaskCatalogue;
import dev.vibrato.commandsystem.ArgParser;
import dev.vibrato.commandsystem.Command;
import dev.vibrato.commandsystem.CommandException;
import dev.vibrato.ui.MessagePriority;

import java.util.Arrays;

public class ListCommand extends Command {
    public ListCommand() {
        super("list", "List all obtainable items");
    }

    @Override
    protected void call(Vibrato mod, ArgParser parser) throws CommandException {
        mod.log("#### LIST OF ALL OBTAINABLE ITEMS ####", MessagePriority.OPTIONAL);
        mod.log(Arrays.toString(TaskCatalogue.resourceNames().toArray()), MessagePriority.OPTIONAL);
        mod.log("############# END LIST ###############", MessagePriority.OPTIONAL);
    }
}
