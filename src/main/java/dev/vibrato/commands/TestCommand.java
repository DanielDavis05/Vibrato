package dev.vibrato.commands;

import dev.vibrato.Vibrato;
import dev.vibrato.Playground;
import dev.vibrato.commandsystem.Arg;
import dev.vibrato.commandsystem.ArgParser;
import dev.vibrato.commandsystem.Command;
import dev.vibrato.commandsystem.CommandException;

public class TestCommand extends Command {

    public TestCommand() throws CommandException {
        super("test", "Generic command for testing", new Arg(String.class, "extra", "", 0));
    }

    @Override
    protected void call(Vibrato mod, ArgParser parser) throws CommandException {
        Playground.TEMP_TEST_FUNCTION(mod, parser.get(String.class));
        finish();
    }
}