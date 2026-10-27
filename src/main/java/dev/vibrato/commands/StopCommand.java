package dev.vibrato.commands;

import dev.vibrato.Vibrato;
import dev.vibrato.fleet.FleetBootstrap;
import dev.vibrato.commandsystem.ArgParser;
import dev.vibrato.commandsystem.Command;

public class StopCommand extends Command {

    public StopCommand() {
        super("stop", "Stop the current task and pause Director autostart for this session");
    }

    @Override
    protected void call(Vibrato mod, ArgParser parser) {
        FleetBootstrap.stopForSession();
        mod.getUserTaskChain().cancel(mod);
        mod.log("Stopped the current task. Director autostart is paused until " + mod.getModSettings().getCommandPrefix() + "play or reconnect.");
        finish();
    }
}
