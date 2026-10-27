package dev.vibrato.commands;

import dev.vibrato.Vibrato;
import dev.vibrato.commandsystem.ArgParser;
import dev.vibrato.commandsystem.Command;
import dev.vibrato.util.helpers.WorldHelper;

public class CoordsCommand extends Command {
    public CoordsCommand() {
        super("coords", "Get bot's current coordinates");
    }

    @Override
    protected void call(Vibrato mod, ArgParser parser) {
        mod.log("CURRENT COORDINATES: " + mod.getPlayer().getBlockPos().toShortString() + " (Current dimension: " + WorldHelper.getCurrentDimension() + ")");
        finish();
    }
}
