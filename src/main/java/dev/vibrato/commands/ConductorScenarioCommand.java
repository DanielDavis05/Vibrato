package dev.vibrato.commands;

import dev.vibrato.Vibrato;
import dev.vibrato.commandsystem.Arg;
import dev.vibrato.commandsystem.ArgParser;
import dev.vibrato.commandsystem.Command;
import dev.vibrato.commandsystem.CommandException;
import dev.vibrato.validation.ConductorScenarioTask;

public final class ConductorScenarioCommand extends Command {
    public ConductorScenarioCommand() throws CommandException {
        super("conductor_scenario", "Run a bounded mode/permission scenario with server-confirmed evidence", new Arg(String.class, "scenario"));
    }
    @Override protected void call(Vibrato mod, ArgParser parser) throws CommandException {
        String name = parser.get(String.class);
        if (!ConductorScenarioTask.NAMES.contains(name)) throw new CommandException("Unknown scenario: " + name);
        mod.runUserTask(new ConductorScenarioTask(name), this::finish);
    }
}
