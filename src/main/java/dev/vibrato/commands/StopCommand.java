package dev.vibrato.commands;

import dev.vibrato.Vibrato;
import dev.vibrato.commandsystem.ArgParser;
import dev.vibrato.commandsystem.Command;

public class StopCommand extends Command {

    public StopCommand() {
        super("stop", "Stop task runner (stops all automation)");
    }

    @Override
    protected void call(Vibrato mod, ArgParser parser) {
        mod.getUserTaskChain().cancel(mod);
        finish();
    }
}
