package dev.vibrato.commands;

import dev.vibrato.Vibrato;
import dev.vibrato.commandsystem.Arg;
import dev.vibrato.commandsystem.ArgParser;
import dev.vibrato.commandsystem.Command;
import dev.vibrato.commandsystem.CommandException;
import dev.vibrato.director.Persona;
import dev.vibrato.director.Personas;
import dev.vibrato.fleet.FleetBootstrap;

public class PlayCommand extends Command {
    public PlayCommand() throws CommandException {
        super("play", "Start autonomous play, optionally choosing a persona", new Arg(String.class, "persona", null, 0));
    }

    @Override
    protected void call(Vibrato mod, ArgParser parser) throws CommandException {
        String name = parser.get(String.class);
        Persona persona = name == null ? null : Personas.byName(name);
        if (name != null && persona == null) {
            throw new CommandException("Unknown persona '" + name + "'. Choose: " +
                    String.join(", ", Personas.all().stream().map(Persona::name).toList()));
        }
        Persona started = FleetBootstrap.startDirector(mod, persona, this::finish);
        mod.log("Autonomous play started · " + started.name() + ". Use " + mod.getModSettings().getCommandPrefix() + "stop to pause.");
    }
}
