package dev.vibrato.commands;

import dev.vibrato.Vibrato;
import dev.vibrato.commandsystem.Arg;
import dev.vibrato.commandsystem.ArgParser;
import dev.vibrato.commandsystem.Command;
import dev.vibrato.commandsystem.CommandException;
import dev.vibrato.tasks.resources.CollectFoodTask;

public class FoodCommand extends Command {
    public FoodCommand() throws CommandException {
        super("food", "Collects a certain amount of food", new Arg(Integer.class, "count"));
    }

    @Override
    protected void call(Vibrato mod, ArgParser parser) throws CommandException {
        mod.runUserTask(new CollectFoodTask(parser.get(Integer.class)), this::finish);
    }
}