package dev.vibrato.commands;

import dev.vibrato.Vibrato;
import dev.vibrato.commandsystem.Arg;
import dev.vibrato.commandsystem.ArgParser;
import dev.vibrato.commandsystem.Command;
import dev.vibrato.commandsystem.CommandException;
import dev.vibrato.tasks.movement.GoToStrongholdPortalTask;
import dev.vibrato.tasks.movement.LocateDesertTempleTask;

public class LocateStructureCommand extends Command {

    public LocateStructureCommand() throws CommandException {
        super("locate_structure", "Locate a world generated structure.", new Arg(Structure.class, "structure"));
    }

    @Override
    protected void call(Vibrato mod, ArgParser parser) throws CommandException {
        Structure structure = parser.get(Structure.class);
        switch (structure) {
            case STRONGHOLD:
                mod.runUserTask(new GoToStrongholdPortalTask(1), this::finish);
                break;
            case DESERT_TEMPLE:
                mod.runUserTask(new LocateDesertTempleTask(), this::finish);
                break;
        }
    }

    public enum Structure {
        DESERT_TEMPLE,
        STRONGHOLD
    }
}