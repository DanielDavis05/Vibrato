package dev.vibrato.commands;

import dev.vibrato.Vibrato;
import dev.vibrato.Debug;
import dev.vibrato.commandsystem.Arg;
import dev.vibrato.commandsystem.ArgParser;
import dev.vibrato.commandsystem.Command;
import dev.vibrato.commandsystem.CommandException;
import dev.vibrato.mixins.SimpleOptionAccessor;
import net.minecraft.client.MinecraftClient;

public class SetGammaCommand extends Command {

    public SetGammaCommand() throws CommandException {
        super("gamma", "sets the brightness to a value", new Arg(Double.class, "gamma", 1.0, 0));
    }

    @Override
    protected void call(Vibrato mod, ArgParser parser) throws CommandException {
        double gammaValue = parser.get(Double.class);
        Debug.logMessage("Gamma set to " + gammaValue);
        // Set the raw value so gammas outside the slider range (fullbright) are accepted.
        ((SimpleOptionAccessor) (Object) MinecraftClient.getInstance().options.getGamma()).setRawValue(gammaValue);

    }

}
