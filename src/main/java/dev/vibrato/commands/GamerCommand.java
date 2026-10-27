package dev.vibrato.commands;

import dev.vibrato.Vibrato;
import dev.vibrato.commandsystem.ArgParser;
import dev.vibrato.commandsystem.Command;
import dev.vibrato.tasks.speedrun.BeatMinecraft2Task;

public class GamerCommand extends Command {
    public GamerCommand() {
        super("gamer", "Beats the game");
    }

    @Override
    protected void call(Vibrato mod, ArgParser parser) {
        mod.runUserTask(new BeatMinecraft2Task(), this::finish);
    }
}
